package de.codesourcery.inversek;

import java.awt.Dimension;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;

import com.badlogic.gdx.math.Vector2;

import de.codesourcery.inversek.Node.NodeType;

public class Main 
{
	protected static final float ROTATE_DELTA_DEGREES = 1;

	public static void main(String[] args) 
	{
		new Main().run();
	}

	private final Model model;
	private final MyPanel panel;
	private final KeyboardInput input = new KeyboardInput();
	private final Bone effector;

	public Main() 
	{
		model = new Model();

		final Joint j1 = model.addJoint( "Joint #0" , 45 );
		final Joint j2 = model.addJoint( "Joint #1" , 90 );
		final Joint j3 = model.addJoint( "Joint #2" , 90 );
		
		model.addBone( "Bone #0", j1,j2 , 25 );
		model.addBone( "Bone #1", j2, j3 , 25 );
		effector = model.addBone( "Bone #2", j3, null , 25 );

		model.applyForwardKinematics();

		panel = new MyPanel( model );
	}


	public void run() {

		panel.setPreferredSize( new Dimension(640,480 ) );

		final JFrame frame = new JFrame("test");
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		frame.getContentPane().add( panel );
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		input.attach(panel);

		// main loop

		long previous = System.currentTimeMillis();
		while ( true ) 
		{
			long now = System.currentTimeMillis();
			float deltaSeconds = (now - previous)/1000f;
			previous = now;

			processKeyboardInput();
			
			if ( panel.desiredPositionChanged ) 
			{
				final Vector2 target = panel.viewToModel( panel.desiredPosition );
				System.out.println("Desired target => view: "+panel.desiredPosition+" (world: "+target+")");
				model.applyInverseKinematics( effector , target );
				panel.desiredPositionChanged = false;
			}

			panel.tick( deltaSeconds );

			try {
				Thread.sleep( 15 );
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void processKeyboardInput()
	{
		if ( panel.selectedNode == null || ! panel.selectedNode.hasType( NodeType.JOINT ) ) 
		{
			return;
		}

		if ( input.isPressed( KeyEvent.VK_LEFT ) ) 
		{
			((Joint) panel.selectedNode).addOrientation( ROTATE_DELTA_DEGREES );
			model.applyForwardKinematics();
		} 
		else if ( input.isPressed( KeyEvent.VK_RIGHT ) )
		{ 
			((Joint) panel.selectedNode).addOrientation( -ROTATE_DELTA_DEGREES );
			model.applyForwardKinematics();
		}
	}
}
