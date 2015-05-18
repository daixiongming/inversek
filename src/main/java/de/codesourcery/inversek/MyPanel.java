package de.codesourcery.inversek;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.SharedLibraryLoader;

import de.codesourcery.inversek.WorldModel.Ball;

public final class MyPanel extends JPanel implements ITickListener , IMathSupport
{
	private static final Color BACKGROUND_COLOR = Color.WHITE;

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

	private final RobotArm robotModel;

	private final Object RENDER_LOCK = new Object();

	private boolean initialized = false;

	private final FPSTracker fpsTracker = new FPSTracker();

	private final WorldModel worldModel;

	private final BufferedImage[] buffers = new BufferedImage[2];
	private final Graphics2D[] graphics = new Graphics2D[2];
	private int bufferIdx = 0;

	private KinematicsChain debugChain;

	public volatile Node<?> selectedNode;
	public volatile Node<?> hoveredNode;

	public volatile Point currentMousePosition;

	public volatile Point desiredPosition;

	private final OrthographicCamera camera;

	static {
		new SharedLibraryLoader().load("gdx");
	}

	public final MouseInput mouseInput = new MouseInput();

	protected static final class Box
	{
		public final Vector2 p0 = new Vector2();
		public final Vector2 p1 = new Vector2();
		public final Vector2 p2 = new Vector2();
		public final Vector2 p3 = new Vector2();

		public Box() {
		}

		public boolean contains(float x,float y)
		{
			float xMin = min( min( min( p0.x , p1.x ) , p2.x ) , p3.x );
			if ( x < xMin ) {
				return false;
			}

			float yMin = min( min( min( p0.y , p1.y ) , p2.y ) , p3.y );
			if ( y < yMin ) {
				return false;
			}

			float xMax = max( max( max( p0.x , p1.x ) , p2.x ) , p3.x );
			if ( x > xMax ) {
				return false;
			}
			float yMax = max( max( max( p0.y , p1.y ) , p2.y ) , p3.y );
			return y <= yMax;
		}

		public void getOrientedLine(Vector2 start,Vector2 end)
		{
			start.set( p0 ).add( p3 ).scl(0.5f);
			end.set( p1 ).add( p2 ).scl(0.5f);
		}

		public Vector2 getMin(Vector2 vec) {
			vec.x = min( min( min( p0.x , p1.x ) , p2.x ) , p3.x );
			vec.y = min( min( min( p0.y , p1.y ) , p2.y ) , p3.y );
			return vec;
		}

		public Vector2 getMax(Vector2 vec) {
			vec.x = max( max( max( p0.x , p1.x ) , p2.x ) , p3.x );
			vec.y = max( max( max( p0.y , p1.y ) , p2.y ) , p3.y );
			return vec;
		}

		private static float min(float a,float b) {
			return a < b ? a : b;
		}

		private static float max(float a,float b) {
			return a > b ? a : b;
		}

		public Vector2 getCenter(Vector2 vec)
		{
			final Vector2 c0 = p0.cpy().add( p1 ).scl(0.5f);
			final Vector2 c1 = p2.cpy().add( p3 ).scl(0.5f);
			vec.set( c0.add( c1 ).scl(0.5f) );
			return vec;
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

	public Node<?> getNodeAt(int x,int y)
	{
		final Box boundingBox = new Box();
		for ( KinematicsChain chain : robotModel.getModel().getChains() )
		{
			final Node<?> n = chain.stream()
					.filter( node ->
					{
						getBoundingBoxInScreenCoords( node , boundingBox );
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
		mouseInput.attach(this);
		setFocusable(true);
		requestFocus();
	}

	protected void getBoundingBoxInScreenCoords(Node<?> node,Box boundingBox)
	{
		switch( node.getType() )
		{
			case BONE:
				setBoundingBox( (Bone) node , boundingBox );
				break;
			case JOINT:
				getBoundingBox((Joint) node,boundingBox);
				break;
			default:
				throw new RuntimeException("Internal error,unhandled switch/case: "+node.getType());
		}
		modelToView( boundingBox );
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		g.drawImage( getFrontBufferImage() , 0 , 0 , null );
		Toolkit.getDefaultToolkit().sync();
	}

	private int debugBones = 10;

	public void render(float deltaSeconds)
	{
		synchronized(RENDER_LOCK)
		{
			clearBackBuffer();

			// render world
			renderWorld();

			// render robot arm
			if ( debugBones > 0 )
			{
				debugBones--;
				for ( Bone b : robotModel.getModel().getChains().get(0).getBones()) {
					System.out.println("Bone "+b.getId()+": start="+b.start+" -> end="+b.end+", center: "+b.getCenter() );
				}
				for ( Joint j : robotModel.getModel().getChains().get(0).getJoints() ) {
					System.out.println( j+" @ "+j.position);
				}
				System.out.println("---");
			}

			renderDebugChain();

			robotModel.getModel().getChains().forEach( chain -> chain.getBones().forEach( this::renderBone) );
			robotModel.getModel().getChains().forEach( chain -> chain.getJoints().forEach( this::renderJoint ) );

			renderFPS( deltaSeconds );

			renderMousePosition();

			renderSelectionInfo();

			renderDesiredPosition();

			swapBuffers();
		}
	}

	private void renderDebugChain()
	{
		KinematicsChain tmp = debugChain;
		if ( tmp != null )
		{
			final Graphics2D graphics = getBackBufferGraphics();
			graphics.setColor(Color.GREEN);

			final Vector2 p0 = new Vector2();
			final Vector2 p1 = new Vector2();
			for ( Bone b : tmp.getBones() )
			{
				p0.set( b.start );
				p1.set( b.end );
				modelToView( p0,p0 );
				modelToView( p1,p1 );
				renderLine( p0 , p1 );
			}

			final Gripper endBone = (Gripper) tmp.getEndBone();
			p0.set( endBone.getPositioningEnd() );
			modelToView(p0,p0);
			final float centerX = p0.x;
			final float centerY = p0.y;
			graphics.drawLine( (int) centerX -5 , (int)centerY , (int)centerX + 5 , (int)centerY );
			graphics.drawLine( (int) centerX , (int)centerY-5 , (int)centerX , (int)centerY+5 );
		}
	}

	public void setDebugRender(KinematicsChain chain) {
		this.debugChain = chain;
	}

	private void renderWorld() {

		// render floor
		final Vector2 p = new Vector2(0,0);
		modelToView( p , p);

		final Graphics2D graphics = getBackBufferGraphics();
		graphics.setColor(Color.BLACK);
		graphics.drawLine( 0 , (int) p.y , getWidth() , (int) p.y );

		// render robot arm base
		final Body robotBase = robotModel.getBase();
		getBackBufferGraphics().setColor( ROBOT_BASE_COLOR );
		renderBox( robotBase.getPosition() , Constants.ROBOTBASE_WIDTH , Constants.ROBOTBASE_HEIGHT , true );

		// render world objects
		final List<Ball> balls = new ArrayList<>( worldModel.getBalls() ); // need to copy here since destroying balls will modify the collection
		for ( Ball b : balls )
		{
			if ( ! renderBall( b ) ) {
				worldModel.destroyBall( b );
			}
		}
	}

	private void renderBox(Vector2 centerInWorldCoords,float xExtent,float yExtent,boolean filled)
	{
		renderBox(centerInWorldCoords,xExtent,yExtent,Vector2.Zero,0,filled);
	}

	private void renderBox(Vector2 centerInWorldCoords,float xExtent,float yExtent,Vector2 rotationCenter,float angleInDegrees,boolean filled)
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

	private void renderDesiredPosition()
	{
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
				final Joint joint = ((Joint) selectedNode);

				final float angleRad = joint.getBody().getJointAngle();
				float angleDeg = radToDeg( angleRad );
				float angle = angleDeg;
				if ( angle < 0 ) {
					angle = 360+angle;
				}
				float angleNorm = normalizeAngleInDeg( angle );

				details = " , model orientation: "+joint.getOrientationDegrees()+"° (box2d: rad="+angleRad+",deg="+angleDeg+",flipped: "+angle+", norm: "+angleNorm;
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

	private void renderJoint(Joint joint)
	{
		getBackBufferGraphics().setColor( getNodeColor(joint,JOINT_COLOR) );
		renderCircle( getJointPosition(joint) , Constants.JOINT_RENDER_RADIUS );
	}

	private Vector2 getJointPosition(Joint joint)
	{
		final Vector2 jointPosition = new Vector2();

		if ( joint.predecessor == null )
		{
			jointPosition.set( 0 , Constants.ROBOTBASE_HEIGHT + Constants.JOINT_RADIUS );
		}
		else
		{
			// calculate joint position by
			// intersecting lines through the two bones
			// it connects
			tmpBox.set( joint.predecessor.getBody().getPosition() ,
					joint.predecessor.length ,
					Constants.BONE_THICKNESS ,
					radToDeg( joint.predecessor.getBody().getAngle() ) );

			final Vector2 start = new Vector2();
			final Vector2 end = new Vector2();
			tmpBox.getOrientedLine( start , end );

			final Vector2 direction = end.cpy().sub( start ).nor().scl( Constants.JOINT_RADIUS);
			jointPosition.set( end ).add( direction );
		}
		return jointPosition;
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
			tmpBox.set( modelCenterCoords , 2*modelRadius , 2*modelRadius , 0 );
			modelToView( tmpBox );

			final Vector2 min = tmpBox.getMin( new Vector2() );
			final Vector2 max = tmpBox.getMax( new Vector2() );

			final Graphics2D graphics = getBackBufferGraphics();
			graphics.fillArc( (int) min.x , (int) min.y , (int) (max.x-min.x) , (int) (max.y - min.y) , 0 , 360 );
			graphics.setColor(Color.BLACK);

			graphics.drawLine( (int) centerX -5 , (int) centerY , (int) centerX + 5 , (int)centerY );
			graphics.drawLine( (int) centerX , (int)  centerY-5 , (int) centerX , (int)centerY+5 );
		}
		return isOnScreen;
	}

	private Color getNodeColor(Node<?> n,Color regular)
	{
		if ( selectedNode == n ) {
			return SELECTION_COLOR;
		}
		if ( hoveredNode == n ) {
			return HOVER_COLOR;
		}
		return regular;
	}

	private void getBoundingBox(Joint joint,Box r)
	{
		final Vector2 center = getJointPosition(joint);
		r.set( center , 2*Constants.JOINT_RENDER_RADIUS , 2*Constants.JOINT_RENDER_RADIUS , 0);
	}

	private void setBoundingBox(Bone bone,Box box)
	{
		box.set( bone.getBody().getPosition() ,
				bone.length ,
				Constants.BONE_THICKNESS ,
				radToDeg( bone.getBody().getAngle() ) );
	}

	private void renderBone(Bone bone)
	{
		final Graphics2D graphics = getBackBufferGraphics();

		final Color regularColor  = bone instanceof Gripper ? GRIPPER_BONE_COLOR : BONE_COLOR;
		graphics.setColor( getNodeColor(bone, regularColor ));

		setBoundingBox( bone , tmpBox );
		renderBox( tmpBox , true );

		// TODO: Remove debug rendering
		graphics.setColor( Color.RED );
		Vector2 debugP0 = bone.start.cpy();
		Vector2 debugP1 = bone.end.cpy();
		modelToView(debugP0,debugP0);
		modelToView(debugP1,debugP1);
		renderLine( debugP0 , debugP1 );

		if ( bone instanceof Gripper)
		{
			final Gripper gripper = (Gripper) bone;

			// render base plate
			// TODO: Maybe use gripper.getCurrentBaseplateLength() instead ?
			graphics.setColor( GRIPPER_BASEPLATE_COLOR );
			renderBox( gripper.getBasePlateBody().getPosition() , Constants.BASEPLATE_THICKNESS , gripper.getMaxBaseplateLength() ,
					Vector2.Zero,
					radToDeg( gripper.getBasePlateBody().getAngle() ) , true );

			// render upper claw
			graphics.setColor( GRIPPER_UPPER_CLAW_COLOR );
			renderBox( gripper.getUpperClawBody().getPosition() ,
					gripper.getClawLength() , Constants.CLAW_THICKNESS ,
					Vector2.Zero,
					radToDeg( gripper.getUpperClawBody().getAngle() ) , true );

			// render lower claw
			graphics.setColor( GRIPPER_LOWER_CLAW_COLOR );
			renderBox( gripper.getLowerClawBody().getPosition() ,
					gripper.getClawLength() , Constants.CLAW_THICKNESS ,
					Vector2.Zero,
					radToDeg( gripper.getLowerClawBody().getAngle() ) , true );

			graphics.setColor( Color.RED );

			Vector2 tmp = gripper.getPositioningEnd().cpy();
			modelToView( tmp , tmp );
			graphics.drawLine( (int) (tmp.x -5), (int) tmp.y, (int) (tmp.x + 5  ) , (int) tmp.y );
			graphics.drawLine( (int) tmp.x, (int) (tmp.y-5), (int) tmp.x , (int) (tmp.y+5) );
		}
	}

	private void renderLine(Vector2 p0,Vector2 p1)
	{
		getBackBufferGraphics().drawLine( (int) p0.x , (int) p0.y , (int) p1.x,(int) p1.y );
	}

	public Vector2 viewToModel(Point point)
	{
		final Vector3 coords = new Vector3(point.x , getHeight() - point.y,0);
		unproject( coords , 0 , 0 , getWidth() , getHeight() );

		// libgdx assumes the origin of view coordinates in the LOWER left corner
		// of the screen, need to fix Y coordinate here
		return new Vector2(coords.x,coords.y);
	}

	private void modelToView(Box box)
	{
		modelToView( box.p0 , box.p0 );
		modelToView( box.p1 , box.p1 );
		modelToView( box.p2 , box.p2 );
		modelToView( box.p3 , box.p3 );
	}

	private void modelToView(Vector2 modelVector,Vector2 viewVector)
	{
		Vector3 result = new Vector3( modelVector , 0 );
		camera.project( result  , 0 , 0 , getWidth() , getHeight() );

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
		graphics.setColor( BACKGROUND_COLOR );
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
		synchronized( RENDER_LOCK )
		{
			final int panelWidth = getWidth();
			final int panelHeight = getHeight();
			if ( ! initialized || buffers[0].getWidth() != panelWidth || buffers[0].getHeight() != panelHeight )
			{
				final BufferedImage oldFrontBuffer = buffers[ (bufferIdx+1) % 2 ];

				if ( graphics[0] != null)
				{
					graphics[0].dispose();
					graphics[1].dispose();
				}

				buffers[0] = new BufferedImage( panelWidth , panelHeight , BufferedImage.TYPE_INT_RGB);
				buffers[1] = new BufferedImage( panelWidth , panelHeight , BufferedImage.TYPE_INT_RGB);
				graphics[0] = buffers[0].createGraphics();
				graphics[1] = buffers[1].createGraphics();

				final Map<Object,Object> hints = new HashMap<>();

				hints.put( RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON );
				hints.put( RenderingHints.KEY_RENDERING , RenderingHints.VALUE_RENDER_QUALITY);
				hints.put( RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON );
				hints.put( RenderingHints.KEY_RENDERING , RenderingHints.VALUE_RENDER_QUALITY);

				graphics[0].addRenderingHints( hints );
				graphics[1].addRenderingHints( hints );

				graphics[0].setColor( BACKGROUND_COLOR );
				graphics[0].fillRect( 0 , 0 , panelWidth , panelHeight );

				graphics[1].setColor( BACKGROUND_COLOR );
				graphics[1].fillRect( 0 , 0 , panelWidth , panelHeight );

				updateCamera(camera,panelWidth,panelHeight);

				if ( oldFrontBuffer != null )
				{
					// doing a render() call here would introduce a race condition
					// with things happening in the main thread (box2d model updates etc.) that
					// will sometimes lead to graphics glitching... one (slow) way of fixing this
					// would be to introduce a global lock that's held while the main loop is not
					// within the render() call. A quick'n'dirty hack that works ok'ish (assuming the
					// FPS is not incredibly low) is to just render a scaled version of the
					// last front buffer and hope that the next render() call happens soon
					final Graphics2D newFrontBufferGfx = graphics[ (bufferIdx+1) % 2 ];
					newFrontBufferGfx.drawImage(oldFrontBuffer,0,0,panelWidth,panelHeight,null);
				}
				initialized = true;
			}
		}
	}

	private static void updateCamera(OrthographicCamera camera,int viewportWidth,int viewportHeight)
	{
		camera.setToOrtho( true , viewportWidth , viewportHeight);

		camera.direction.set( 0 , 0, 1 );
		camera.position.set(0,0.9f,-5f);
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