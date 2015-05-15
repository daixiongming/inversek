package de.codesourcery.inversek;

public final class JointAnimator implements ITickListener 
{
	private static final float ACTUATOR_SPEED = 90f;
	private static final float EPSILON = 0.1f;
	
	private final Joint joint;
	private final float desiredAngle;
	private float elapsedSeconds;

	public JointAnimator(Joint joint,float desiredAngle) {
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
		elapsedSeconds+= deltaSeconds;
		
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
		
		if ( elapsedSeconds > 3 ) {
			System.err.println("Moving joint "+joint+" from "+currentAngle+" degrees to "+desiredAngle+" degrees took more than "+elapsedSeconds+" seconds");			
		}
		return true;
	}
}