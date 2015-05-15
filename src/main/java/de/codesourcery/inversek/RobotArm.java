package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.math.Vector2;

import de.codesourcery.inversek.ISolver.Outcome;
import de.codesourcery.inversek.Joint.MovementRange;

public class RobotArm implements ITickListener {

	private static final float ACTUATOR_SPEED = 180f;
	private static final float EPSILON = 0.1f;
	
	private final Model model;
	private float solveTimeSecs;
	private ISolver currentSolver;
	private final Map<String,Actuator> actuators = new HashMap<>();
	
	protected static final class ActuatorMover implements ITickListener 
	{
		private final Joint joint;
		private final float desiredAngle;

		public ActuatorMover(Joint joint,float desiredAngle) {
			this.joint = joint;
			this.desiredAngle = desiredAngle;
		}
		
		private float getMinDistance(float desiredAngle,float currentAngle) 
		{
			float ccwDelta;
			float cwDelta;			
			if ( desiredAngle >= currentAngle ) {
				ccwDelta = desiredAngle - currentAngle;
				cwDelta = (360-desiredAngle) + currentAngle;
			} else { 
				cwDelta = currentAngle - desiredAngle;
				ccwDelta = (360-currentAngle) + desiredAngle;
			}
			return Math.min( ccwDelta, cwDelta );
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
			
			final float speed = getMinDistance( desiredAngle , currentAngle ) <= 2 ? ACTUATOR_SPEED*0.05f : ACTUATOR_SPEED;
			float inc;
			if ( ccwDelta < cwDelta ) {
				inc = speed * deltaSeconds;
			} else {
				inc = - speed * deltaSeconds;
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
		chain.addBone( "Bone #3", j4, null , 25 );

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
	
	private ISolver createSolver(Vector2 desiredPoint) 
	{
		final KinematicsChain chain = this.model.getChains().get(0).createCopy();
		
		final Joint rootJoint = chain.getRootJoint();
		final IConstraintValidator validator = new IConstraintValidator() 
		{
			@Override
			public boolean isInvalidConfiguration(KinematicsChain chain) 
			{
				for ( Bone b : chain.getBones() ) 
				{
					if ( b.start.y < rootJoint.position.y || b.end.y < rootJoint.position.y ) {
						return true;
					}
				}
				return false; 
			}
		};
		return new AsyncSolverWrapper( new CCDSolver(chain, desiredPoint, validator ) );
	}
	
	public void moveArm(Vector2 desiredPoint) 
	{
		if ( hasFinishedMoving() ) {
			currentSolver = createSolver(desiredPoint);			
			solveTimeSecs=0;
		} else {
			System.err.println("Arm has not finished moving yet");
		}
	}
	
	private void solve(float deltaSeconds) 
	{
		if ( currentSolver == null) {
			return;
		}

		final Outcome outcome = currentSolver.solve(100);
		solveTimeSecs+=deltaSeconds;
		if ( outcome == Outcome.SUCCESS )
		{
			System.out.println("Found solution in "+solveTimeSecs*1000+" millis");
			currentSolver.getChain().visitJoints( joint -> 
			{
				if ( Main.DEBUG ) {
					System.out.println("*** Joint "+joint+" is set to angle "+joint.getOrientationDegrees());
				}
				actuators.get( joint.getId() ).setDesiredAngle( joint.getOrientationDegrees() );
				return true;
			});
			currentSolver = null;
		} 
		else if ( outcome == Outcome.FAILURE) {
			System.err.println("Failed to solve motion constraints after "+solveTimeSecs*1000+" millis");
			currentSolver = null;
		}
	}
	
	public boolean hasFinishedMoving() 
	{
		return currentSolver == null && actuators.values().stream().noneMatch( Actuator::isMoving );
	}

	@Override
	public boolean tick(float deltaSeconds) 
	{
		solve(deltaSeconds);
		actuators.values().forEach( act -> act.tick( deltaSeconds ) );
		model.getChains().forEach( chain -> chain.applyForwardKinematics() );
		return true;
	}
}