package de.codesourcery.inversek;

import com.badlogic.gdx.math.Vector2;

public class Gripper extends Bone 
{
	private final float basePlateLength;
	private final float clawLength;
	private final Vector2 positioningEnd = new Vector2();
	
	private float open=1.0f;
	
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
}