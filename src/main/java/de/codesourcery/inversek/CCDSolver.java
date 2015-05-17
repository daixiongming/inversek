package de.codesourcery.inversek;

import java.util.Random;

import com.badlogic.gdx.math.Vector2;

public class CCDSolver implements ISolver 
{
	private static final double EPSILON = 0.0001; 

	private static final double DESIRED_ARRIVAL_DST = 0.05f;

	protected static final int RANDOM_RETRIES = 200;

	protected static final int FAILURE_RETRY_COUNT = 100;

	private static final int MAX_ITERATIONS = 200;

	private static final double MIN_CHANGE  = 0.01;

	private final Random rnd = new Random(System.currentTimeMillis());

	private final KinematicsChain chain;
	private final Bone endBone;
	private final Vector2 desiredPosition;
	private final ICompletionCallback completionCallback;

	// runtime state
	private Outcome finalResult;

	private int iterations = MAX_ITERATIONS;
	private int failureRetriesLeft = FAILURE_RETRY_COUNT;
	private int randomRetriesLeft = RANDOM_RETRIES;
	
	private final IConstraintValidator constraintValidator;

	public CCDSolver(KinematicsChain chain,Vector2 desiredPosition,IConstraintValidator validator,ICompletionCallback completionCallback) 
	{
		this.chain = chain;
		this.endBone = chain.getEndBone();
		this.desiredPosition = desiredPosition.cpy();
		this.constraintValidator = validator;
		this.completionCallback = completionCallback;
	}

	@Override
	public boolean hasFinished() {
		return finalResult != null;
	}

	@Override
	public Outcome solve(final int maxIterations) 
	{
		if ( finalResult != null ) {
			return finalResult;
		}
		
		Outcome outcome = Outcome.FAILURE;

		int localIterations = iterations;
		int localRandomRetriesLeft = randomRetriesLeft;
		int localFailureRetriesLeft = failureRetriesLeft;
		try 
		{
			for ( int i = maxIterations ; i > 0 ; i--) 
			{
				outcome = singleIteration(endBone, desiredPosition);
				localIterations--;
				switch(outcome)
				{
					case FAILURE:
						if ( localFailureRetriesLeft-- > 0 ) {
							outcome = Outcome.PROCESSING;
						} 
						else 
						{
							if ( localRandomRetriesLeft-- <= 0 ) {
								return terminalResult( Outcome.FAILURE );						
							}
							
							// restart from new random position
							chain.setRandomJointPositions(rnd);
							chain.applyForwardKinematics();
							localIterations = MAX_ITERATIONS;
							localFailureRetriesLeft = FAILURE_RETRY_COUNT;
							outcome = Outcome.PROCESSING;
							// end: restart
						}
						break;
					case PROCESSING:
						if ( localIterations < 0 ) 
						{
							if ( localRandomRetriesLeft-- <= 0 ) {
								return terminalResult( Outcome.FAILURE );						
							}				
							// restart from new random position
							chain.setRandomJointPositions(rnd);
							chain.applyForwardKinematics();
							localIterations = MAX_ITERATIONS;
							localFailureRetriesLeft = FAILURE_RETRY_COUNT;
							outcome = Outcome.PROCESSING;
							// end: restart
						} else {	
							localFailureRetriesLeft = FAILURE_RETRY_COUNT;
						}
						break;
					case SUCCESS:
						return terminalResult( outcome );
					default:
						throw new RuntimeException("Unhandled switch/case: "+outcome);
				}
			}
			return outcome;
		} 
		finally 
		{
			iterations = localIterations;
			randomRetriesLeft = localRandomRetriesLeft;
			failureRetriesLeft = localFailureRetriesLeft;
		}
	}
	
	private Outcome terminalResult(Outcome result) {
		finalResult = result;
		return result;
	}

	private Outcome singleIteration(final Bone lastBone,Vector2 desiredPosition) 
	{
		Joint currentJoint = lastBone.jointA;

		/* Code heavily inspired by http://www.ryanjuckett.com/programming/cyclic-coordinate-descent-in-2d/
		 */

		final float initialDistance = lastBone.getPositioningEnd().dst(desiredPosition);
		
		final Vector2 curToEnd= new Vector2();
		final Vector2 curToTarget = new Vector2();
		
		while ( true ) 
		{
			final Bone currentBone = currentJoint == null ? null : currentJoint.successor;
			if ( currentBone == null ) 
			{
				// check for termination
				final float currentDst = lastBone.getPositioningEnd().dst2( desiredPosition ); 				
				if ( currentDst <= DESIRED_ARRIVAL_DST*DESIRED_ARRIVAL_DST ) {
					return constraintValidator.isInvalidConfiguration( chain ) ? Outcome.FAILURE : Outcome.SUCCESS;
				}

				if ( Math.abs( currentDst - initialDistance ) >= MIN_CHANGE ) {
					return Outcome.PROCESSING;
				}
				return Outcome.FAILURE;				
			}

			// Get the vector from the current bone to the end effector position.			
			curToEnd.set( lastBone.getPositioningEnd() ).sub( currentBone.getCenter() );
			final double curToEndMag = curToEnd.len();

			// Get the vector from the current bone to the target position.
			curToTarget.set( desiredPosition ).sub( currentBone.getCenter() );
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
			currentJoint.addOrientation( (float) rotDeg );

			// update bone positions
			if ( currentJoint.successor != null  ) {
				currentJoint.successor.forwardKinematics();				
			}
			
			// check for termination
			if ( lastBone.getPositioningEnd().dst2( desiredPosition ) <= DESIRED_ARRIVAL_DST*DESIRED_ARRIVAL_DST ) {
				return constraintValidator.isInvalidConfiguration( chain ) ? Outcome.FAILURE : Outcome.SUCCESS;
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

	@Override
	public KinematicsChain getChain() {
		return chain;
	}
	
	@Override
	public ICompletionCallback getCompletionCallback() {
		return completionCallback;
	}
}
