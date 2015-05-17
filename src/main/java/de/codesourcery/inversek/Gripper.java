package de.codesourcery.inversek;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;

public class Gripper extends Bone 
{
	private final float basePlateLength;
	private final float clawLength;
	private final Vector2 positioningEnd = new Vector2();
	
	private float open=1.0f;
	
	/* Gripper looks like this:
	 * 
	 *       BJ222222222
	 *       B
	 * XXXXXXB
	 *       B
	 *       BJ111111111
	 *       
	 * where 
	 * 
	 * X = bone the gripper is attached to
	 * B = Gripper base plate
	 * J = Prismatic joint
	 * 1 = lower part of claw
	 * 2 = upper part of claw
	 */	
	
	private Body basePlateBody;
	private Body lowerClawBody;
	private Body upperClawBody;
	
	private PrismaticJoint lowerJoint;
	private PrismaticJoint upperJoint;
	
	private Gripper(Gripper other, Joint jointA, Joint jointB) 
	{
		super(other, jointA, jointB);
		this.basePlateLength = other.basePlateLength;
		this.clawLength = other.clawLength;
		this.open = other.open;
	}

	public Gripper(String name, Joint jointA, Joint jointB, float armLength, float basePlateLength,float clawLength) {
		super(name, jointA, jointB, armLength);
		this.basePlateLength = basePlateLength;
		this.clawLength = clawLength;
	}
	
	public Gripper createCopy(Joint jointA, Joint jointB) {
		return new Gripper(this,jointA,jointB);
	}
	
	public float getClawLength() {
		return clawLength;
	}
	
	public float getMaxBaseplateLength() {
		return basePlateLength;
	}
	
	@Override
	public void forwardKinematics() 
	{
		super.forwardKinematics();
		positioningEnd.set( end ).sub( start ).nor().scl( clawLength/2f ).add( end );
	}
	
	public float getCurrentBaseplateLength() {
		return basePlateLength*open;
	}
	
	@Override
	public Vector2 getPositioningEnd() {
		return positioningEnd;
	}

	public Body getBasePlateBody() {
		return basePlateBody;
	}

	public void setBasePlateBody(Body basePlateBody) {
		this.basePlateBody = basePlateBody;
	}

	public Body getLowerClawBody() {
		return lowerClawBody;
	}

	public void setLowerClawBody(Body lowerClawBody) {
		this.lowerClawBody = lowerClawBody;
	}

	public Body getUpperClawBody() {
		return upperClawBody;
	}

	public void setUpperClawBody(Body upperClawBody) {
		this.upperClawBody = upperClawBody;
	}

	public PrismaticJoint getLowerJoint() {
		return lowerJoint;
	}

	public void setLowerJoint(PrismaticJoint lowerJoint) {
		this.lowerJoint = lowerJoint;
	}

	public PrismaticJoint getUpperJoint() {
		return upperJoint;
	}

	public void setUpperJoint(PrismaticJoint upperJoint) {
		this.upperJoint = upperJoint;
	}
}