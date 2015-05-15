package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.math.Vector2;

import de.codesourcery.inversek.ISolver.Outcome;
import de.codesourcery.inversek.Joint.MovementRange;

public class RobotArm implements ITickListener {

	private static final float ACTUATOR_SPEED = 70f;
	private static final float EPSILON = 0.5f;
	
	private final Model model;
	private final Bone effector;
	
	private final Map<String,Actuator> actuators = new HashMap<>();
	
	protected static final class ActuatorMover implements ITickListener 
	{
		private final Joint joint;
		private final float desiredAngle;

		public ActuatorMover(Joint joint,float desiredAngle) {
			this.joint = joint;
			this.desiredAngle = desiredAngle;
		}

		@Override
		public boolean tick(float deltaSeconds) 
		{
			final float currentAngle = joint.getOrientationDegrees();
			
			float ccwDelta;
			float cwDelta;
			
			if ( desiredAngle >= currentAngle ) {
				ccwDelta = desiredAngle - currentAngle;
				cwDelta = (360-desiredAngle) + currentAngle;
			} else { // desired angle < currentAngle
				cwDelta = currentAngle - desiredAngle;
				ccwDelta = (360-currentAngle) + desiredAngle;
			}
			
			if ( Math.abs(ccwDelta) < EPSILON || Math.abs( cwDelta ) < EPSILON ) 
			{
				if ( Main.DEBUG ) {
					System.out.println("Joint "+joint+" finished moving (actual: "+currentAngle+", desired: "+desiredAngle+")");
				}
				return false;
			}
			
			float inc;
			if ( ccwDelta < cwDelta ) {
				inc = ACTUATOR_SPEED * deltaSeconds;
			} else {
				inc = - ACTUATOR_SPEED * deltaSeconds;
			}

			if ( Main.DEBUG ) {
				System.out.println("Moving joint "+joint+" by "+inc+" degrees to get from "+currentAngle+" to "+desiredAngle);
			}
			joint.addOrientation( inc );
			return true;
		}
	}
	
	protected static final class Actuator implements ITickListener 
	{
		private final Joint joint;
		private final List<Runnable> tasks = new ArrayList<>();
		private final TickListenerContainer container = new TickListenerContainer();

		public Actuator(Joint joint) {
			this.joint = joint;
		}
		
		public boolean isMoving() {
			return ! tasks.isEmpty() || ! container.isEmpty();
		}
		
		@Override
		public boolean tick(float deltaSeconds) 
		{
			if ( container.isEmpty() && ! tasks.isEmpty() ) 
			{
				tasks.remove(0).run();
			}
			container.tick( deltaSeconds );
			return true;
		}
		
		public void setDesiredAngle(float angle) 
		{
			if ( Main.DEBUG ) {
				System.out.println("QUEUED: Actuator( "+this.joint+") will move from "+this.joint.getOrientationDegrees()+" -> "+angle);
			}
			tasks.add( () ->  
			{ 
				if ( Main.DEBUG ) {
					System.out.println("ACTIVE: Actuator( "+this.joint+") now moving from "+this.joint.getOrientationDegrees()+" -> "+angle);
				}
				container.add( new ActuatorMover( this.joint , angle ) ); 
			});
		}
	}
	
	public RobotArm() 
	{
		KinematicsChain chain = new KinematicsChain();

		final Joint j1 = chain.addJoint( "Joint #0" , 45 );
		final Joint j2 = chain.addJoint( "Joint #1" , 90 );
		final Joint j3 = chain.addJoint( "Joint #2" , 90 );
		final Joint j4 = chain.addJoint( "Joint #4" , 90 );
		
		j2.setRange( new MovementRange( 270 , 90 ) );
		j3.setRange( new MovementRange( 270 , 90 ) );
		j4.setRange( new MovementRange( 270 , 90 ) );
		
		chain.addBone( "Bone #0", j1,j2 , 25 );
		chain.addBone( "Bone #1", j2, j3 , 25 );
		chain.addBone( "Bone #2", j3, j4 , 25 );
		
		effector = chain.addBone( "Bone #3", j4, null , 25 );

		chain.applyForwardKinematics();
		
		chain.visitJoints( joint -> 
		{ 
			actuators.put( joint.getId() , new Actuator((Joint) joint) );
			return true;
		});
		
		model = new Model();
		model.addKinematicsChain( chain );
	}
	
	public Model getModel() {
		return model;
	}
	
	public boolean moveArm(Vector2 desiredPoint) 
	{
		if ( ! hasFinishedMoving() ) {
			System.err.println("Arm has not finished moving yet");
			return false;
		}
		
		final KinematicsChain m = this.model.getChains().get(0).createCopy();
		
		CCDSolver solver = new CCDSolver(m, m.getEndBone() , desiredPoint);
		
		ISolver.Outcome outcome;
		do {
			outcome = solver.solve();
		} while ( outcome == Outcome.PROCESSING );
		
		if ( outcome == Outcome.SUCCESS )
		{
			m.visitJoints( joint -> 
			{
				if ( Main.DEBUG ) {
					System.out.println("*** Joint "+joint+" is set to angle "+((Joint) joint).getOrientationDegrees());
				}
				actuators.get( joint.getId() ).setDesiredAngle( ((Joint) joint).getOrientationDegrees() );
				return true;
			});
			return true;
		} else {
			System.err.println("Failed to solve motion constraints");			
		}
		return false;
	}
	
	public boolean hasFinishedMoving() 
	{
		return actuators.values().stream().noneMatch( Actuator::isMoving );
	}

	@Override
	public boolean tick(float deltaSeconds) 
	{
		actuators.values().forEach( act -> act.tick( deltaSeconds ) );
		model.getChains().forEach( chain -> chain.applyForwardKinematics() );
		return true;
	}
}