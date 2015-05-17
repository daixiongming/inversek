package de.codesourcery.inversek;

import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;

public final class JointAnimator implements ITickListener, IMathSupport
{
	private static final float EPSILON = 0.5f;
	
	private final Joint joint;
	private final float desiredAngle;
	private boolean motorStarted;

	public JointAnimator(Joint joint,float desiredAngle) {
		this.joint = joint;
		this.desiredAngle = desiredAngle;
	}
	
	@Override
	public boolean tick(float deltaSeconds) 
	{
		final RevoluteJoint rJoint = joint.getBody();

		float currentAngle = normalizeAngleInDeg( box2dAngleToDeg( rJoint.getJointAngle() ) );
		if ( ! motorStarted ) 
		{
			float ccwDelta;
			float cwDelta;
			
			if ( desiredAngle >= currentAngle ) {
				ccwDelta = desiredAngle - currentAngle;
				cwDelta = (360-desiredAngle) + currentAngle;
			} else { // desired angle < currentAngle
				cwDelta = currentAngle - desiredAngle;
				ccwDelta = (360-currentAngle) + desiredAngle;
			}
			
			float degPerSecond = Constants.MOTOR_SPEED;
			if ( ccwDelta > cwDelta ) { // move clockwise
				degPerSecond *= -1;
			}
			float lowerLimit = radToDeg( rJoint.getLowerLimit());
			float upperLimit = radToDeg( rJoint.getUpperLimit());

			System.out.println("Moving "+joint+" from "+currentAngle+"° (box2d limits: "+lowerLimit+","+upperLimit+") to "+desiredAngle+"° by "+degPerSecond+" degrees/s");
			rJoint.setMotorSpeed( degPerSecond );
			motorStarted = true;
			return true;
		} 
//		System.out.println( joint+" is at angle "+rJoint.getJointAngle()+", motor: "+rJoint.isMotorEnabled()+
//				",limit_enabled: "+rJoint.isLimitEnabled()+", "
//						+ "torque: "+rJoint.getMaxMotorTorque()+", active: "+rJoint.isActive()+" , speed: "+radToDeg( rJoint.getMotorSpeed() ) );
		if ( Math.abs(currentAngle - desiredAngle) < EPSILON )
		{
			rJoint.setMotorSpeed( 0 );
				
//				if ( Main.DEBUG ) {
					System.out.println("Joint "+joint+" finished moving (actual: "+currentAngle+", desired: "+desiredAngle+")");
//				}
			return false;
		}
		return true;
	}
}