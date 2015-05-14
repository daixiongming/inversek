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
	
	public Range range = new Range(0,360);
	
	public static final class Range {
		
		private final float degStart;
		private final float degEnd;
		
		public Range(float degStart,float degEnd) {
			this.degStart = degStart;
			this.degEnd = degEnd;
		}
		
		public float clamp(float value) 
		{
			while ( value >= 360 ) {
				value -= 360;
			}
			while ( value < 0 ) {
				value += 360;
			}
			if ( value >= degStart && value <= degEnd ) {
				return value;
			}
			float d1 = Math.abs( value - degStart );
			float d2 = Math.abs( value - degEnd );
			if ( d1 < d2 ) {
				System.out.println("Clamping "+value+" to "+degStart);
				return degStart;
			}
			System.out.println("Clamping "+value+" to "+degEnd);
			return degEnd;
		}
	}
	
	public Joint(String name,float radius,float orientation) 
	{
		super(name,NodeType.JOINT);
		if ( radius <= 0 ) {
			throw new IllegalArgumentException("radius needs to be > 0,was: "+radius);
		}
		this.radius = radius;
		setOrientation(orientation);
	}
	
	public void setRange(Range range) {
		if (range == null) {
			throw new IllegalArgumentException("range must not be NULL");
		}
		this.range = range;
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
		orientationDegrees = range.clamp( degrees );
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
