package de.codesourcery.inversek;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.SharedLibraryLoader;

import de.codesourcery.inversek.WorldModel.Ball;

public final class MyPanel extends JPanel implements ITickListener , IMathSupport
{
	private static final Color ROBOT_BASE_COLOR = Color.BLACK;
	
	private static final Color JOINT_COLOR = Color.RED;
	
	private static final Color BALL_COLOR = Color.GREEN;
	
	private static final Color GRIPPER_BONE_COLOR = Color.BLACK;
	private static final Color GRIPPER_BASEPLATE_COLOR = Color.YELLOW;
	private static final Color GRIPPER_LOWER_CLAW_COLOR = Color.RED;
	private static final Color GRIPPER_UPPER_CLAW_COLOR = Color.CYAN;
	
	private static final Color BONE_COLOR = Color.BLUE;
	
	protected static final Color SELECTION_COLOR = Color.GREEN;
	protected static final Color HOVER_COLOR = Color.MAGENTA;
	
	protected static final Box tmpBox = new Box();
	
	protected static final Matrix3 MODEL_MATRIX = new Matrix3().setToScaling(  200, 200 );
	
	private final RobotArm robotModel;
	
	private final Object INIT_LOCK = new Object();
	
	private boolean initialized = false;
	
	private final FPSTracker fpsTracker = new FPSTracker();
	
	private final WorldModel worldModel;
	
	private final BufferedImage[] buffers = new BufferedImage[2]; 
	private final Graphics2D[] graphics = new Graphics2D[2];
	private int bufferIdx = 0;
	
	private int screenCenterX;
	private int screenCenterY;
	
	public volatile Point addBallAt;
	
	public volatile Node<?> selectedNode;
	public volatile Node<?> hoveredNode;
	
	private volatile Point currentMousePosition;
	
	public volatile boolean desiredPositionChanged = false;
	public volatile Point desiredPosition;
	
	private final OrthographicCamera camera;
	
	static {
		new SharedLibraryLoader().load("gdx");
	}
	
	private final MouseAdapter mouseListener = new MouseAdapter() 
	{
		public void mouseClicked(java.awt.event.MouseEvent e) 
		{
			if ( e.getButton() == MouseEvent.BUTTON1 ) 
			{
				final Point p = e.getPoint();
				final Node<?> n = getNodeAt( p.x ,p.y );
				if ( n != null && selectedNode != n ) 
				{
					selectedNode = n;
				}
			} 
			else if ( e.getButton() == MouseEvent.BUTTON2 ) 
			{
				if ( addBallAt == null )
				{
					addBallAt = new Point( e.getPoint() );
				}
			}
			else if ( e.getButton() == MouseEvent.BUTTON3 ) 
			{
				if ( desiredPosition == null || ! desiredPosition.equals( e.getPoint() ) ) 
				{
					desiredPosition = new Point( e.getPoint() );
					desiredPositionChanged = true;
				}
			}
		}
		
		public void mouseMoved(java.awt.event.MouseEvent e) 
		{
			final Point p = e.getPoint();
			currentMousePosition = new Point(p);
			
			final Node<?> n = getNodeAt( p.x ,p.y );
			if ( hoveredNode != n ) {
				hoveredNode = n;
			}	
		}
	};
	
	protected static final class Box 
	{
		public final Vector2 p0 = new Vector2();
		public final Vector2 p1 = new Vector2();
		public final Vector2 p2 = new Vector2();
		public final Vector2 p3 = new Vector2();
		
		public Box() {
		}
		
		public void getOrientedLine(Vector2 start,Vector2 end) 
		{
			start.set( p0 ).add( p3 ).scl(0.5f);
			end.set( p1 ).add( p2 ).scl(0.5f);
		}
		
		public void set(Vector2 centerInWorldCoords,float xExtent,float yExtent,float angleInDegrees) 
		{
			p0.set(-xExtent/2, yExtent/2);
			p1.set( xExtent/2, yExtent/2);
			p2.set( xExtent/2,-yExtent/2);
			p3.set(-xExtent/2,-yExtent/2);
			
			if ( angleInDegrees != 0 ) 
			{
				p0.rotate( angleInDegrees );
				p1.rotate( angleInDegrees );
				p2.rotate( angleInDegrees );
				p3.rotate( angleInDegrees );
			}
			
			p0.add( centerInWorldCoords );
			p1.add( centerInWorldCoords );
			p2.add( centerInWorldCoords );
			p3.add( centerInWorldCoords );
		}
	}	
	
	private Node<?> getNodeAt(int x,int y) 
	{
		final Rectangle boundingBox = new Rectangle();
		for ( KinematicsChain chain : robotModel.getModel().getChains() ) 
		{
			final Node<?> n = chain.stream()
					.filter( node -> 
					{
						getBoundingBox( node , boundingBox );
						return boundingBox.contains( x,y );
					})
					.findFirst().orElse( null );
			if ( n != null ) {
				return n;
			}
		}
		return null;
	}
	
	public MyPanel(RobotArm arm,WorldModel worldModel) 
	{
		camera = new OrthographicCamera( 320 , 240 );
		updateCamera( camera , 320 , 240 );
		
		this.robotModel = arm;
		this.worldModel = worldModel;
		addMouseListener( mouseListener );
		addMouseMotionListener( mouseListener );
		setFocusable(true);
		requestFocus();
	}
	
	protected void getBoundingBox(Node<?> node,Rectangle boundingBox) 
	{
		switch( node.getType() ) 
		{
			case BONE: 
				getBoundingBox((Bone) node,boundingBox);
				return;
			case JOINT:
				getBoundingBox((Joint) node,boundingBox);
				return;
		}
		throw new RuntimeException("Internal error,unhandled switch/case: "+node.getType());		
	}
	
	@Override
	protected void paintComponent(Graphics g) 
	{
		super.paintComponent(g);
		g.drawImage( getFrontBufferImage() , 0 , 0 , null );
		Toolkit.getDefaultToolkit().sync();
	}
	
	public void render(float deltaSeconds) 
	{
		screenCenterX = getWidth()/2;
		screenCenterY = getHeight() / 2;
		clearBackBuffer();
		
		// render world
		renderWorld();
		
		// render robot arm
		robotModel.getModel().getChains().forEach( chain -> 
		{
			chain.visit( this::renderNode );
		});
		
		renderFPS( deltaSeconds );
		
		renderMousePosition();
		
		renderSelectionInfo();
		
		renderDesiredPosition();
		
		swapBuffers();
	}
	
	private void renderWorld() {
		
		// render floor
		final Vector2 p = new Vector2();
		modelToView( new Vector2(0,worldModel.getFloorY()) , p);
		
		final Graphics2D graphics = getBackBufferGraphics();
		graphics.setColor(Color.BLACK);
		graphics.drawLine( 0 , (int) p.y , getWidth() , (int) p.y );
		
		// render robot arm base
		final Body robotBase = robotModel.getBase();
		getBackBufferGraphics().setColor( ROBOT_BASE_COLOR );
		renderBox( robotBase.getPosition() , Constants.ROBOTBASE_WIDTH , Constants.ROBOTBASE_HEIGHT , 0 , true );
		
		// render world objects
		final List<Ball> balls = new ArrayList<>( worldModel.getBalls() ); // need to copy here since destroying balls will modify the collection
		for ( Ball b : balls ) 
		{
			if ( ! renderBall( b ) ) {
				worldModel.destroyBall( b );
			}
		}
	}

	private void renderBox(Vector2 centerInWorldCoords,float xExtent,float yExtent,float angleInDegrees,boolean filled) 
	{
		tmpBox.set( centerInWorldCoords , xExtent , yExtent , angleInDegrees );
		renderBox( tmpBox , filled );
	}

	private void renderBox(Box box,boolean filled) 
	{
		final int x[] = new int[4];
		final int y[] = new int[4];
		
		final Vector2 tmp = new Vector2();
		
		modelToView( tmpBox.p0 , tmp );
		x[0] = (int) tmp.x;
		y[0] = (int) tmp.y;
		
		modelToView( tmpBox.p1 , tmp );
		x[1] = (int) tmp.x;
		y[1] = (int) tmp.y;
		
		modelToView( tmpBox.p2 , tmp );
		x[2] = (int) tmp.x;
		y[2] = (int) tmp.y;
		
		modelToView( tmpBox.p3 , tmp );
		x[3] = (int) tmp.x;
		y[3] = (int) tmp.y;
		
		if ( filled ) {
			getBackBufferGraphics().fillPolygon( x , y , 4 );
		} else {
			getBackBufferGraphics().drawPolygon( x , y , 4 );
		}
	}
	
	private boolean renderBall(Ball ball) 
	{
		getBackBufferGraphics().setColor( BALL_COLOR );
		return renderCircle( ball.getPosition() , ball.radius );
	}
	
	private void renderFPS(float deltaSeconds) 
	{
		fpsTracker.renderFPS( deltaSeconds );
		final BufferedImage image = getBackBufferImage();
		getBackBufferGraphics().drawImage( fpsTracker.getImage() , 0, image.getHeight() - fpsTracker.getSize().height , null );
	}

	private void renderDesiredPosition() {
		
		if ( desiredPosition == null ) {
			return;
		}
		
		final Graphics2D graphics = getBackBufferGraphics();
		
		graphics.setColor(Color.RED);
		graphics.drawLine( desiredPosition.x-5 , desiredPosition.y , desiredPosition.x+5, desiredPosition.y );
		graphics.drawLine( desiredPosition.x , desiredPosition.y-5 , desiredPosition.x, desiredPosition.y+5 );
	}

	private void renderSelectionInfo() 
	{
		if ( selectedNode == null ) {
			return;
		}
		
		String details="";
		switch(selectedNode.getType()) {
			case BONE:
				Bone b = (Bone) selectedNode;
				if ( b.jointB == null ) {
					details = " , connected to "+b.jointA;
				} else {
					details = " , connects "+b.jointA+" with "+b.jointB;
				}
				break;
			case JOINT:
				details = " , orientation: "+((Joint) selectedNode).getOrientationDegrees()+"°";
				break;
			default:
				break;
		}
		
		final Graphics2D graphics = getBackBufferGraphics();
		
		graphics.setColor(Color.BLACK);
		graphics.drawString( "SELECTION: "+selectedNode.getId()+details, 5 , 15 );
	}
	
	private void renderMousePosition() 
	{
		final Point tmp = currentMousePosition;
		if ( tmp == null ) {
			return;
		}

		final Vector2 modelCoords = viewToModel( tmp );
		final Vector2 viewCoords = new Vector2();
		modelToView( modelCoords , viewCoords );
		
		final Graphics2D graphics = getBackBufferGraphics();
		graphics.setColor(Color.BLACK);
		graphics.drawString( "Mouse @ "+tmp+" (model: "+modelCoords+" / converted: "+viewCoords+")", 5 , 35 );
	}	

	private boolean renderNode(Node<?> n) 
	{
		switch( n.getType() ) {
			case BONE:
				renderBone( (Bone) n);
				break;
			case JOINT:
				renderJoint( (Joint) n);
				break;
			default:
				throw new RuntimeException("Internal error,unhandled switch/case: "+n.getType());
		}
		return true;
	}

	private void renderJoint(Joint joint) 
	{
		// calculate joint position by
		// intersecting lines through the two bones
		// it connects
		final Vector2 jointPosition = new Vector2();
		
		if ( joint.predecessor == null ) 
		{
			jointPosition.set( 0 , Constants.ROBOTBASE_HEIGHT + Constants.JOINT_RADIUS );
		} 
		else 
		{
			tmpBox.set( joint.predecessor.getBody().getPosition() , 
					joint.predecessor.length , 
					Constants.BONE_THICKNESS , radToDeg( joint.predecessor.getBody().getAngle() ) );
		
			final Vector2 start = new Vector2();
			final Vector2 end = new Vector2();
			tmpBox.getOrientedLine( start , end );
			
			final Vector2 direction = end.cpy().sub( start ).nor().scl( Constants.JOINT_RADIUS);
			jointPosition.set( end ).add( direction );
		}

		getBackBufferGraphics().setColor( getNodeColor(joint,JOINT_COLOR) );		
		renderCircle( jointPosition,joint.radius);
	}
	
	private boolean renderCircle(Vector2 modelCenterCoords,float modelRadius) 
	{
		final Vector2 screenPos = new Vector2();
		modelToView( modelCenterCoords , screenPos );
		
		final float centerX = screenPos.x;
		final float centerY = screenPos.y;
		
		final boolean isOnScreen = !( centerX < 0 || centerY < 0 || centerX > getWidth() || centerY > getHeight() );
		
		if ( isOnScreen ) 
		{
			// transform radius
			screenPos.x = modelRadius;
			screenPos.y = modelRadius;
			modelToView( screenPos , screenPos );
			
			final float dx = screenPos.x - (float) screenCenterX;
			final float dy = screenCenterY - screenPos.y;
			
			final float radWidth = dx*2f;
			final float radHeight = dy*2f;
			
			final Graphics2D graphics = getBackBufferGraphics();
			
			graphics.fillArc( (int) ( centerX - radWidth/2f) ,(int) (centerY-radHeight/2f) , (int) radWidth , (int) radHeight, 0 , 360 );
			graphics.setColor(Color.BLACK);
			
			graphics.drawLine( (int) centerX -5 , (int)centerY , (int)centerX + 5 , (int)centerY );
			graphics.drawLine( (int) centerX , (int)centerY-5 , (int)centerX , (int)centerY+5 );
		}
		return isOnScreen;
	}
	
	private Color getNodeColor(Node<?> n,Color regular) {
		if ( selectedNode == n ) {
			return SELECTION_COLOR;
		}
		if ( hoveredNode == n ) {
			return HOVER_COLOR;
		}
		return regular;
	}
	
	private void getBoundingBox(Joint n,Rectangle r) 
	{
		final Vector2 screenPos = new Vector2();
		modelToView( n.position , screenPos );
		
		final float centerX = screenPos.x;
		final float centerY = screenPos.y;
		
		// transform radius
		screenPos.x = n.radius;
		screenPos.y = 0;
		modelToView( screenPos , screenPos );
		
		final float dx = screenPos.x - screenCenterX;
		final float dy = screenPos.y - screenCenterY;
		
		final float scrRadius = (float) Math.sqrt( dx*dx + dy*dy );
		
		r.x = (int) ( centerX - scrRadius/2);
		r.y = (int) (centerY - scrRadius/2);
		r.width = r.height = (int) scrRadius;
	}
	
	private void renderBone(Bone bone) 
	{
		final Graphics2D graphics = getBackBufferGraphics();
		
		final Color regularColor  = bone instanceof Gripper ? GRIPPER_BONE_COLOR : BONE_COLOR;
		graphics.setColor( getNodeColor(bone, regularColor ));
		renderBox( bone.getBody().getPosition() , bone.length , Constants.BONE_THICKNESS , radToDeg( bone.getBody().getAngle() ) , true );
		
		if ( bone instanceof Gripper) 
		{
			final Gripper gripper = (Gripper) bone;
			
			// render base plate
			// TODO: Maybe use gripper.getCurrentBaseplateLength() instead ?
			graphics.setColor( GRIPPER_BASEPLATE_COLOR );
			renderBox( gripper.getBasePlateBody().getPosition() , Constants.BASEPLATE_THICKNESS , gripper.getMaxBaseplateLength() , 
					radToDeg( gripper.getBasePlateBody().getAngle() ) , true );
			
			// render upper claw
			graphics.setColor( GRIPPER_UPPER_CLAW_COLOR );
			renderBox( gripper.getUpperClawBody().getPosition() ,
					gripper.getClawLength() , Constants.CLAW_THICKNESS , radToDeg( gripper.getUpperClawBody().getAngle() ) , true );
			
			// render lower claw
			graphics.setColor( GRIPPER_LOWER_CLAW_COLOR );
			renderBox( gripper.getLowerClawBody().getPosition() ,
					gripper.getClawLength() , Constants.CLAW_THICKNESS , radToDeg( gripper.getLowerClawBody().getAngle() ) , true );			
		} 
	}
	
	private void getBoundingBox(Bone bone,Rectangle r) 
	{
		final Vector2 screenPos = new Vector2();
		modelToView( bone.start , screenPos );
		
		final float p0X = screenPos.x;
		final float p0Y = screenPos.y;
		
		modelToView( bone.end , screenPos );
		
		final float p1X = screenPos.x;
		final float p1Y = screenPos.y;			
		
		float x = Math.min(p0X,p1X);
		float y = Math.min(p0Y,p1Y);
		
		r.x = (int) x;
		r.y = (int) y;
		r.width = Math.abs( (int) (p1X - p0X) );
		r.height = Math.abs( (int) (p1Y - p0Y) );
	}
	
	public Vector2 viewToModel(Point point) 
	{
		final Vector3 coords = new Vector3(point.x , getHeight() - point.y,0);
		unproject( coords , 0 , 0 , getWidth() , getHeight() );
		
		// libgdx assumes the origin of view coordinates in the LOWER left corner
		// of the screen, need to fix Y coordinate here		
		return new Vector2(coords.x,coords.y);
	}	
	
	private void modelToView(Vector2 modelVector,Vector2 viewVector) 
	{
		Vector3 result = new Vector3( modelVector , 0 );
		camera.project( result  , 0 , 0 , getWidth() , getHeight() );
		
		// libgdx assumes the origin of view coordinates in the LOWER left corner
		// of the screen, need to fix Y coordinate here
		viewVector.x = result.x;
		viewVector.y = result.y+1;
	}	
	
	public Vector3 unproject (Vector3 screenCoords, float viewportX, float viewportY, float viewportWidth, float viewportHeight) {
		float x = screenCoords.x, y = screenCoords.y;
		x = x - viewportX;
		y = viewportHeight - y - 1;
		y = y - viewportY;
		screenCoords.x = (2 * x) / viewportWidth - 1;
		screenCoords.y = (2 * y) / viewportHeight - 1;
		screenCoords.z = 2 * screenCoords.z - 1;
		screenCoords.prj(camera.invProjectionView);
		return screenCoords;
	}	
	
	private void clearBackBuffer() 
	{
		final BufferedImage buffer = getBackBufferImage();
		final Graphics2D graphics = getBackBufferGraphics();
		graphics.setColor( Color.WHITE );
		graphics.fillRect( 0 , 0 , buffer.getWidth() , buffer.getHeight() );
	}	
	
	private BufferedImage getFrontBufferImage() 
	{
		maybeInit();
		return buffers[ (bufferIdx+1) % 2 ];
	}
	
	private Graphics2D getBackBufferGraphics() 
	{
		maybeInit();
		return graphics[ bufferIdx % 2 ];
	}		
	
	private BufferedImage getBackBufferImage() 
	{
		maybeInit();
		return buffers[ bufferIdx % 2 ];
	}			
	
	private void swapBuffers() 
	{
		bufferIdx++;
	}
	
	private void maybeInit() 
	{
		synchronized( INIT_LOCK ) 
		{
			if ( ! initialized || buffers[0].getWidth() != getWidth() || buffers[0].getHeight() != getHeight() ) 
			{
				if ( graphics[0] != null) 
				{
					graphics[0].dispose();
				}
				if ( graphics[1] != null) { 
					graphics[1].dispose();
				}
				buffers[0] = new BufferedImage( getWidth() , getHeight() , BufferedImage.TYPE_INT_RGB);
				buffers[1] = new BufferedImage( getWidth() , getHeight() , BufferedImage.TYPE_INT_RGB);
				graphics[0] = buffers[0].createGraphics();
				graphics[1] = buffers[1].createGraphics();
				
				graphics[0].getRenderingHints().put( RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON );
				graphics[0].getRenderingHints().put( RenderingHints.KEY_RENDERING , RenderingHints.VALUE_RENDER_QUALITY);
				graphics[1].getRenderingHints().put( RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON );
				graphics[1].getRenderingHints().put( RenderingHints.KEY_RENDERING , RenderingHints.VALUE_RENDER_QUALITY);
				
				updateCamera(camera,getWidth(),getHeight());
				
				initialized = true;
				render(1);
			}
		}
	}
	
	private static void updateCamera(OrthographicCamera camera,int viewportWidth,int viewportHeight) 
	{
		camera.setToOrtho( true , viewportWidth , viewportHeight);
		
		camera.direction.set( 0 , 0, 1 );
		camera.position.set(0,0,-5f);
		camera.near = 0;		
		camera.far = 100;
		camera.zoom = 0.005f;
		
		System.out.println("Setting camera viewport to "+viewportWidth+" x "+viewportHeight);
		camera.update(true);
	}

	@Override
	public boolean tick(float deltaSeconds) 
	{
		render(deltaSeconds);
		repaint();
		return true;
	}	
}