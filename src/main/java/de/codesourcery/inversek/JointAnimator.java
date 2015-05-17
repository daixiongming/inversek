package de.codesourcery.inversek;

import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;

public final class JointAnimator implements ITickListener, IMathSupport
{
	private static final float EPSILON = 0.5f;
	
	private final Joint joint;
	private final float desiredAngleInDeg;
	private boolean motorStarted;

	public JointAnimator(Joint joint,float desiredAngleInDeg) {
		this.joint = joint;
		this.desiredAngleInDeg = desiredAngleInDeg;
	}
	
	private int ticks = 0;
	
	@Override
	public boolean tick(float deltaSeconds) 
	{
		ticks++;
		
		final RevoluteJoint rJoint = joint.getBody();

		float currentAngle = joint.getBox2dOrientationDegrees();
		if ( ! motorStarted ) 
		{
			float ccwDelta;
			float cwDelta;
			
			if ( desiredAngleInDeg >= currentAngle ) {
				ccwDelta = desiredAngleInDeg - currentAngle;
				cwDelta = (360-desiredAngleInDeg) + currentAngle;
			} else { // desired angle < currentAngle
				cwDelta = currentAngle - desiredAngleInDeg;
				ccwDelta = (360-currentAngle) + desiredAngleInDeg;
			}
			
			float degPerSecond = Constants.MOTOR_SPEED;
			if ( ccwDelta > cwDelta ) { // move clockwise
				degPerSecond *= -1;
			}
			float lowerLimit = radToDeg( rJoint.getLowerLimit());
			float upperLimit = radToDeg( rJoint.getUpperLimit());

			System.out.println("Moving "+joint+" from "+currentAngle+"° (box2d limits: "+lowerLimit+","+upperLimit+") to "+desiredAngleInDeg+"° by "+degPerSecond+" degrees/s");
			rJoint.setMotorSpeed( degPerSecond );
			motorStarted = true;
			return true;
		} 
//		System.out.println( joint+" is at angle "+rJoint.getJointAngle()+", motor: "+rJoint.isMotorEnabled()+
//				",limit_enabled: "+rJoint.isLimitEnabled()+", "
//						+ "torque: "+rJoint.getMaxMotorTorque()+", active: "+rJoint.isActive()+" , speed: "+radToDeg( rJoint.getMotorSpeed() ) );
		final float delta = Math.abs(currentAngle - desiredAngleInDeg);
		if ( delta < EPSILON )
		{
			rJoint.setMotorSpeed( 0 );
				
//				if ( Main.DEBUG ) {
					System.out.println("Joint "+joint+" finished moving (actual: "+currentAngle+", desired: "+desiredAngleInDeg+")");
//				}
			return false;
		} else 
		{
//			if ( ( ticks %10) == 0 ) {
//				System.out.println(joint.getId()+" : delta: "+delta+" , current: "+currentAngle+" , desired: "+desiredAngleInDeg);
//			}
		}
		return true;
	}
}