package de.codesourcery.inversek;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import com.badlogic.gdx.math.Vector2;

public final class MyPanel extends JPanel implements ITickListener
{
	private static final Color JOINT_COLOR = Color.RED;
	private static final Color BONE_COLOR = Color.BLUE;
	
	protected static final Color SELECTION_COLOR = Color.GREEN;
	protected static final Color HOVER_COLOR = Color.MAGENTA;
	
	private final Model model;
	
	private BufferedImage[] buffers = null; 
	private Graphics2D[] graphics = null;
	private int bufferIdx = 0;
	
	private int screenCenterX;
	private int screenCenterY;
	
	public Node selectedNode;
	public Node hoveredNode;
	
	public boolean desiredPositionChanged = false;
	public Point desiredPosition;
	
	private final MouseAdapter mouseListener = new MouseAdapter() 
	{
		public void mouseClicked(java.awt.event.MouseEvent e) 
		{
			if ( e.getButton() == MouseEvent.BUTTON1 ) 
			{
				final Point p = e.getPoint();
				final Node n = getNodeAt( p.x ,p.y );
				if ( n != null && selectedNode != n ) 
				{
					selectedNode = n;
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
			final Node n = getNodeAt( p.x ,p.y );
			if ( hoveredNode != n ) {
				hoveredNode = n;
			}
			
			if ( desiredPosition == null || ! desiredPosition.equals( p ) ) 
			{
				desiredPosition = new Point( p );
				desiredPositionChanged = true;
			}			
		}
	};
	
	public Vector2 viewToModel(Point point) {
		
		/*
		viewVector.x = screenCenterX + modelVector.x;
		viewVector.y = screenCenterY - modelVector.y;		 
		 */
		float x = point.x - screenCenterX;
		float y = screenCenterY - point.y;
		return new Vector2(x,y);
	}
	
	private Node getNodeAt(int x,int y) 
	{
		final Rectangle boundingBox = new Rectangle();
		return model.stream().filter( node -> 
		{
			getBoundingBox( node , boundingBox );
			return boundingBox.contains( x,y );
		}).findFirst().orElse( null );
	}
	
	public MyPanel(Model model) 
	{
		this.model = model;
		addMouseListener( mouseListener );
		addMouseMotionListener( mouseListener );
		setFocusable(true);
		requestFocus();
	}
	
	protected void getBoundingBox(Node node,Rectangle boundingBox) 
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
	}
	
	public void render() 
	{
		screenCenterX = getWidth()/2;
		screenCenterY = getHeight() / 2;
		clearBackBuffer();
		model.visit( this::renderNode );
		
		renderSelectionInfo();
		
		renderDesiredPosition();
		
		swapBuffers();
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
					details = " , connected to "+b.jointA.getName();
				} else {
					details = " , connects "+b.jointA.getName()+" with "+b.jointB.getName();
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
		graphics.drawString( "SELECTION: "+selectedNode.getName()+details, 5 , 15 );
	}

	private boolean renderNode(Node n) 
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

	private void renderJoint(Joint n) 
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
		
		final Graphics2D graphics = getBackBufferGraphics();
		
		graphics.setColor( getNodeColor(n,JOINT_COLOR) );
		graphics.fillArc( (int) ( centerX - scrRadius/2) ,(int) (centerY-scrRadius/2) , (int) scrRadius, (int) scrRadius, 0 , 360 );
	}
	
	private Color getNodeColor(Node n,Color regular) {
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
	
	private void renderBone(Bone n) 
	{
		final Vector2 screenPos = new Vector2();
		modelToView( n.start , screenPos );
		
		final float p0X = screenPos.x;
		final float p0Y = screenPos.y;
		
		modelToView( n.end , screenPos );
		
		final float p1X = screenPos.x;
		final float p1Y = screenPos.y;		
		
		final Graphics2D graphics = getBackBufferGraphics();
		graphics.setColor( getNodeColor(n,BONE_COLOR) );
		graphics.drawLine( (int) p0X , (int) p0Y,(int) p1X,(int) p1Y);
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
	
	private void modelToView(Vector2 modelVector,Vector2 viewVector) 
	{
		viewVector.x = screenCenterX + modelVector.x;
		viewVector.y = screenCenterY - modelVector.y;
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
		if ( buffers == null || buffers[0].getWidth() != getWidth() || buffers[0].getHeight() != getHeight() ) {
			if ( graphics != null) 
			{
				graphics[0].dispose();
				graphics[1].dispose();
			}
			if ( buffers == null ) {
				buffers = new BufferedImage[2];
				graphics = new Graphics2D[2];
			}
			buffers[0] = new BufferedImage( getWidth() , getHeight() , BufferedImage.TYPE_INT_RGB);
			buffers[1] = new BufferedImage( getWidth() , getHeight() , BufferedImage.TYPE_INT_RGB);
			graphics[0] = buffers[0].createGraphics();
			graphics[1] = buffers[1].createGraphics();
			render();
		}
	}

	@Override
	public void tick(float deltaSeconds) 
	{
		render();
		repaint();
	}	
}