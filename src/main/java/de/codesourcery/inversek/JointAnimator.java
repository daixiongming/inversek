package de.codesourcery.inversek;

import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;

public final class JointAnimator implements ITickListener, IMathSupport
{
	private static final float EPSILON = 0.3f;
	
	private final Joint joint;
	private final float desiredAngleInDeg;
	private float degPerSecond;
	
	private boolean motorStarted;

	public JointAnimator(Joint joint,float desiredAngleInDeg) 
	{
		this.joint = joint;
		this.desiredAngleInDeg = desiredAngleInDeg;
		
		float ccwDelta;
		float cwDelta;
		
		final float currentAngle = joint.getBox2dOrientationDegrees();
		
		// pick smallest angle 
		if ( desiredAngleInDeg >= currentAngle ) {
			ccwDelta = desiredAngleInDeg - currentAngle;
			cwDelta = (360-desiredAngleInDeg) + currentAngle;
		} else { // desired angle < currentAngle
			cwDelta = currentAngle - desiredAngleInDeg;
			ccwDelta = (360-currentAngle) + desiredAngleInDeg;
		}
		
		degPerSecond = Constants.JOINT_MOTOR_SPEED_DEG;
		if ( ccwDelta > cwDelta ) { // move clockwise
			degPerSecond *= -1;
		}
	}
	
	@Override
	public boolean tick(float deltaSeconds) 
	{
		final RevoluteJoint rJoint = joint.getBody();
		final float currentAngle = joint.getBox2dOrientationDegrees();
		final float deltaInDeg = Math.abs(currentAngle - desiredAngleInDeg);
		
		if ( deltaInDeg < EPSILON )
		{
			rJoint.setMotorSpeed( 0 );
			System.out.println("Joint "+joint+" finished moving (actual: "+currentAngle+", desired: "+desiredAngleInDeg+")");
			return false;
		}

		final float speed = deltaInDeg > Constants.JOINT_MOTOR_SPEED_DEG ? degPerSecond : degPerSecond*0.3f;
		rJoint.setMotorSpeed( degToRad( speed ) );
		
		if ( ! motorStarted ) 
		{
			float lowerLimit = radToDeg( rJoint.getLowerLimit());
			float upperLimit = radToDeg( rJoint.getUpperLimit());

			System.out.println("Moving "+joint+" from "+currentAngle+"° (box2d limits: "+lowerLimit+","+upperLimit+") to "+desiredAngleInDeg+"° by "+degPerSecond+" degrees/s");
			motorStarted = true;
		} 
		return true;
	}
}