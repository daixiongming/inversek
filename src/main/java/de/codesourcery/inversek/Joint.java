package de.codesourcery.inversek;

import com.badlogic.gdx.math.Vector2;

public class Joint extends Node
{
	private float orientationDegrees = 0;
	private final Vector2 orientation = new Vector2();
	
	public final Vector2 position = new Vector2();
	
	public final float radius;
	
	public Bone successor;
	public Bone predecessor;
	
	public Joint(String name,float radius,float orientation) 
	{
		super(name,NodeType.JOINT);
		if ( radius <= 0 ) {
			throw new IllegalArgumentException("radius needs to be > 0,was: "+radius);
		}
		this.radius = radius;
		setOrientation(orientation);
	}
	
	public boolean hasSuccessor() {
		return successor != null;
	}
	
	public boolean hasPredecessor() {
		return predecessor != null;
	}	
	
	public void setPrecessor(Bone bone) {
		if (bone == null) {
			throw new IllegalArgumentException("bone must not be NULL");
		}
		if ( bone.jointB != this ) {
			throw new IllegalArgumentException("Bone does not start at this joint");
		}
		if ( this.predecessor != null ) {
			throw new IllegalStateException("Predecessor already set");
		}
		this.predecessor = bone;
	}
	
	public void setSuccessor(Bone bone) {
		if (bone == null) {
			throw new IllegalArgumentException("bone must not be NULL");
		}
		if ( bone.jointA != this ) {
			throw new IllegalArgumentException("Bone does not start at this joint");
		}
		if ( this.successor != null ) {
			throw new IllegalStateException("Successor already set");
		}
		this.successor = bone;
	}	
	
	public void setOrientation(float degrees) 
	{
		orientationDegrees = degrees;
		orientation.set(1,0);
		orientation.rotate( degrees );
	}
	
	public void addOrientation(float degreesDelta) 
	{
		setOrientation( this.orientationDegrees + degreesDelta );
	}	
	
	public float getOrientationDegrees() {
		return orientationDegrees;
	}
	
	public float getSumOrientationDegrees() 
	{
		return predecessor == null ? orientationDegrees : orientationDegrees + predecessor.jointA.getSumOrientationDegrees();  
	}
	
	public Vector2 getOrientation() {
		return orientation;
	}	
}
