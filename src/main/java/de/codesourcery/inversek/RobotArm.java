package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

import de.codesourcery.inversek.ISolver.Outcome;
import de.codesourcery.inversek.Joint.MovementRange;

public class RobotArm implements ITickListener , IMathSupport {

	private final WorldModel worldModel;
	private final RobotModel model;
	private float solveTimeSecs;
	private ISolver currentSolver;
	private final Map<String,JointController> jointControllers = new HashMap<>();
	
	private Body base;
	
	protected static final class JointController implements ITickListener 
	{
		private final Joint joint;
		private final List<Runnable> tasks = new ArrayList<>();
		private final TickListenerContainer animators = new TickListenerContainer();

		public JointController(Joint joint) {
			this.joint = joint;
		}
		
		public boolean isMoving() {
			return ! tasks.isEmpty() || ! animators.isEmpty();
		}
		
		public void addTask(Runnable task) {
			this.tasks.add(task);
		}
		
		@Override
		public boolean tick(float deltaSeconds) 
		{
			if ( animators.isEmpty() && ! tasks.isEmpty() ) 
			{
				tasks.remove(0).run();
			}
			animators.tick( deltaSeconds );
			return true;
		}
		
		public void setDesiredAngle(float angle) 
		{
			if ( Main.DEBUG ) {
				System.out.println("QUEUED: Actuator( "+this.joint+") will move from "+this.joint.getOrientationDegrees()+" -> "+angle);
			}
			addTask( () ->  
			{ 
				if ( Main.DEBUG ) {
					System.out.println("ACTIVE: Actuator( "+this.joint+") now moving from "+this.joint.getOrientationDegrees()+" -> "+angle);
				}
				animators.add( new JointAnimator( this.joint , angle ) ); 
			});
		}
	}
	
	public RobotArm(WorldModel worldModel) 
	{
		this.worldModel = worldModel;
		KinematicsChain chain = new KinematicsChain();
		
		final Joint j1 = chain.addJoint( "Joint #0" , 0 );
		j1.position.set(0, Constants.ROBOTBASE_HEIGHT );
		
		final Joint j2 = chain.addJoint( "Joint #1" , 0 );
		final Joint j3 = chain.addJoint( "Joint #2" , 0 );
		final Joint j4 = chain.addJoint( "Joint #4" , 0 );
		
		j2.setRange( new MovementRange( 270 , 90 ) );
		j3.setRange( new MovementRange( 270 , 90 ) );
		j4.setRange( new MovementRange( 270 , 90 ) );
		
		chain.addBone( "Bone #0", j1,j2 , Constants.BONE_BASE_LENGTH );
		chain.addBone( "Bone #1", j2, j3 , Constants.BONE_BASE_LENGTH );
		chain.addBone( "Bone #2", j3, j4 , Constants.BONE_BASE_LENGTH/2 );
		
		chain.addBone( new Gripper("Bone #3", j4, null , 
				Constants.GRIPPER_BONE_LENGTH , 
				Constants.BASEPLASE_LENGTH , 
				Constants.CLAW_LENGTH ) );

		chain.applyForwardKinematics();
		
		chain.visitJoints( joint -> 
		{ 
			jointControllers.put( joint.getId() , new JointController((Joint) joint) );
			return true;
		});
		
		model = new RobotModel();
		model.addKinematicsChain( chain );
		
		worldModel.add( this );
		
		// DEBUG: Compare model with Box2D
		for ( Bone b : model.getChains().get(0).getBones() ) {
			System.out.println("ME positions: "+b.getCenter()+" vs. "+b.getBody().getPosition() );
		}
		
		for ( Joint b : model.getChains().get(0).getJoints() ) {
			System.out.println("ME angles: "+b.getOrientationDegrees()+" vs. "+radToDeg( b.getBody().getJointAngle() ) );
		}
	}
	
	public RobotModel getModel() {
		return model;
	}
	
	private ISolver createSolver(Vector2 desiredPoint) 
	{
		final KinematicsChain input = this.model.getChains().get(0);
		input.syncWithBox2d();
		
		final KinematicsChain chain = input.createCopy();
		
		final IConstraintValidator validator = new IConstraintValidator() 
		{
			private final float minY = worldModel.getFloorY();
			
			@Override
			public boolean isInvalidConfiguration(KinematicsChain chainInFinalConfig) 
			{
				// fast checks first...
				if ( isAnyBoneBelowGroundPlane(chainInFinalConfig) ) {
					return true;
				}
				
				// check end bone orientation
				final Bone endBone = chainInFinalConfig.getEndBone();
				final Vector2 tmp = new Vector2( endBone.end ).sub( endBone.start ).nor();
				final float angle = tmp.angle( new Vector2(0,-1) );
				if ( Math.abs(angle) > 10 ) {
					return true;
				}
				
				// simulate motion to make sure no invalid configurations
				// occur while moving
				final KinematicsChain configToTest = model.getChains().get(0).createCopy();
				
				final List<JointAnimator> animators = new ArrayList<>();
				for ( Joint joint : configToTest.getJoints() ) 
				{
					final float dstAngle = chainInFinalConfig.getJointByID( joint.getId() ).getOrientationDegrees();
					animators.add( new JointAnimator(joint, dstAngle ) );
				}
				while( ! animators.isEmpty() ) 
				{
					for (Iterator<JointAnimator> it = animators.iterator(); it.hasNext();) 
					{
						final JointAnimator m = it.next();
						if ( ! m.tick( 1f / Main.DESIRED_FPS ) ) {
							it.remove();
						}
					}
					
					// recalculate bone positions
					configToTest.applyForwardKinematics();
					
					// validate configuration
					if ( isAnyBoneBelowGroundPlane( configToTest ) ) {
						return true;
					}
				}
				return false; 
			}
			
			private boolean isAnyBoneBelowGroundPlane(KinematicsChain chain) 
			{
				for ( Bone b : chain.getBones() ) 
				{
					if ( b.start.y < minY || b.end.y < minY ) {
						return true;
					}
				}
				for ( Joint j : chain.getJoints() ) 
				{
					if ( j.position.y < minY ) {
						return true;
					}
				}				
				return false;
			}
		};
		return new AsyncSolverWrapper( new CCDSolver(chain, desiredPoint, validator ) );
	}
	
	public boolean moveArm(Vector2 desiredPoint) 
	{
		if ( hasFinishedMoving() ) 
		{
			currentSolver = createSolver(desiredPoint);			
			solveTimeSecs=0;
			return true;
		}
		return false;
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
				jointControllers.get( joint.getId() ).setDesiredAngle( joint.getOrientationDegrees() );
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
		return currentSolver == null && jointControllers.values().stream().noneMatch( JointController::isMoving );
	}

	@Override
	public boolean tick(float deltaSeconds) 
	{
		solve(deltaSeconds);
		jointControllers.values().forEach( act -> act.tick( deltaSeconds ) );
		model.getChains().forEach( chain -> chain.applyForwardKinematics() );
		return true;
	}
	
	public void setBase(Body base) {
		this.base = base;
	}
	
	public Body getBase() {
		return base;
	}
}