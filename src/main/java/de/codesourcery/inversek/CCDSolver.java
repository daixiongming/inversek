package de.codesourcery.inversek;

import java.util.Random;

import com.badlogic.gdx.math.Vector2;

public class CCDSolver implements ISolver 
{
	private static final double EPSILON = 0.0001; 

	private static final double DESIRED_ARRIVAL_DST = 1;

	protected static final boolean CONSTRAINT_MOVEMENT = false;

	protected static final int RANDOM_RETRIES = 100;

	protected static final int FAILURE_RETRY_COUNT = 50;

	private static final int MAX_ITERATIONS = 100;

	private static final double MIN_CHANGE  = 0.01;

	private final Random rnd = new Random(System.currentTimeMillis());

	private final KinematicsChain chain;
	private final Bone endBone;
	private final Vector2 desiredPosition;

	// runtime state
	private Outcome finalResult;

	private int iterations = MAX_ITERATIONS;
	private int retries = FAILURE_RETRY_COUNT;
	private int randomRetriesLeft = RANDOM_RETRIES;

	public CCDSolver(KinematicsChain chain,Vector2 desiredPosition) {
		this.chain = chain;
		this.endBone = chain.getEndBone();
		this.desiredPosition = desiredPosition.cpy();
	}

	@Override
	public boolean hasFinished() {
		return finalResult != null;
	}

	@Override
	public Outcome solve(int maxIterations) 
	{
		if ( finalResult != null ) {
			return finalResult;
		}
		Outcome outcome = Outcome.FAILURE;
		for ( int i = maxIterations ; i > 0 ; i--) 
		{
			outcome = doApplyInverseKinematics(endBone, desiredPosition);
			switch(outcome)
			{
				case FAILURE:
					if ( randomRetriesLeft-- <= 0 ) {
						finalResult = Outcome.FAILURE;
						return Outcome.FAILURE;						
					}
					restartFromRandomPosition();
					outcome = Outcome.PROCESSING;
					break;
				case PROCESSING:
					break;
				case SUCCESS:
					finalResult = Outcome.SUCCESS;
					return outcome;
				default:
					throw new RuntimeException("Unhandled switch/case: "+outcome);
			}
		}
		return outcome;
	}

	private void restartFromRandomPosition() {
		chain.setRandomJointPositions(rnd);
		iterations = MAX_ITERATIONS;
		retries = FAILURE_RETRY_COUNT;
	}

	private Outcome doApplyInverseKinematics(Bone bone,Vector2 desiredPosition) 
	{
		final Outcome result = singleIteration( bone , desiredPosition );
		iterations--;

		switch( result ) {
			case FAILURE:
				retries--;
				if ( retries < 0 ) {
					return Outcome.FAILURE;
				}
				return Outcome.PROCESSING;
			case PROCESSING:
				if ( iterations < 0 ) {
					return Outcome.FAILURE;
				}	
				retries = FAILURE_RETRY_COUNT;
				return result;
			case SUCCESS:
				return result;
			default:
				throw new RuntimeException("Unreachable code reached");
		}
	}

	private Outcome singleIteration(final Bone lastBone,Vector2 desiredPosition) 
	{
		Joint currentJoint = lastBone.jointA;

		/* Code heavily inspired by http://www.ryanjuckett.com/programming/cyclic-coordinate-descent-in-2d/
		 */

		final float initialDistance = lastBone.end.dst(desiredPosition);
		while ( true ) 
		{
			final Bone currentBone = currentJoint == null ? null : currentJoint.successor;
			if ( currentBone == null ) 
			{
				// check for termination
				final float currentDst = lastBone.end.dst( desiredPosition ); 				
				if ( currentDst <= DESIRED_ARRIVAL_DST ) {
					System.out.println("Arrived at destination");
					return Outcome.SUCCESS;
				}	

				if ( Math.abs( currentDst - initialDistance ) >= MIN_CHANGE ) {
					return Outcome.PROCESSING;
				}
				return Outcome.FAILURE;				
			}

			// Get the vector from the current bone to the end effector position.			
			final Vector2 curToEnd = lastBone.end.cpy().sub( currentBone.getCenter() );
			final double curToEndMag = curToEnd.len();

			// Get the vector from the current bone to the target position.
			final Vector2 curToTarget = desiredPosition.cpy().sub( currentBone.getCenter() );
			final double curToTargetMag = curToTarget.len();

			// Get rotation to place the end effector on the line from the current
			// joint position to the target postion.	
			final double cosRotAng;
			final double sinRotAng;
			final double endTargetMag = (curToEndMag*curToTargetMag);
			if( endTargetMag <= EPSILON )
			{
				cosRotAng = 1;
				sinRotAng = 0;
			}
			else
			{
				cosRotAng = (curToEnd.x*curToTarget.x + curToEnd.y*curToTarget.y) / endTargetMag;
				sinRotAng = (curToEnd.x*curToTarget.y - curToEnd.y*curToTarget.x) / endTargetMag;
			}	

			// Clamp the cosine into range when computing the angle (might be out of range
			// due to floating point error).
			double rotAng = Math.acos( Math.max(-1, Math.min(1,cosRotAng) ) );
			if( sinRotAng < 0.0 ) {
				rotAng = -rotAng;	
			}

			// apply rotation

			final double rotDeg = rotAng * (180.0/Math.PI); // convert rad to deg
			if ( Main.DEBUG ) {
				System.out.println("Adjusting "+currentJoint+" by "+rotDeg+" degrees");
			}
			applyJointRotation(currentJoint, rotDeg,lastBone);

			// check for termination
			if ( lastBone.end.dst( desiredPosition ) <= DESIRED_ARRIVAL_DST ) {
				System.out.println("Arrived at destination");
				return Outcome.SUCCESS;
			}			

			// process next joint
			if ( currentJoint.predecessor != null ) 
			{
				currentJoint = currentJoint.predecessor.jointA;
			} else {
				currentJoint = null;
			}
		} 
	}

	private void applyJointRotation(Joint currentJoint, final double rotDeg,Bone lastBone) 
	{
		final float oldRotation = currentJoint.getOrientationDegrees();

		currentJoint.addOrientation( (float) rotDeg );

		// update bone positions
		if ( currentJoint.successor != null  ) {
			currentJoint.successor.forwardKinematics();				
		}

		if ( CONSTRAINT_MOVEMENT && ! isValidConfiguration(lastBone) ) 
		{
			currentJoint.addOrientation( oldRotation );

			// update bone positions
			if ( currentJoint.successor != null  ) {
				currentJoint.successor.forwardKinematics();				
			}			
		}
	}

	private boolean isValidConfiguration(Bone endBone) 
	{
		float y = chain.getRootJoint().position.y;
		if ( chain.getJoints().stream().anyMatch( joint -> joint.position.y < y ) ) {
			return false;
		}
		return endBone.end.y >= y;
	}

	@Override
	public KinematicsChain getChain() {
		return chain;
	}	
}
