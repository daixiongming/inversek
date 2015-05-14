package de.codesourcery.inversek;

import javax.swing.text.html.HTMLDocument.HTMLReader.IsindexAction;

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
		
		public final float[] degStart;
		public final float[] degEnd;
		
		public Range(float degStart,float degEnd) 
		{
			if ( degStart < degEnd ) {
				this.degStart = new float[]{ degStart };
				this.degEnd = new float[]  { degEnd   };
			} else {
				this.degStart = new float[]{ degStart ,      0 };
				this.degEnd = new float[]  {      360 , degEnd };
			}
		}
		
		@Override
		public String toString() {
			return "Range[ "+degStart+" -> "+degEnd+" ]";
		}
		
		public float getMaxAngleCCW() {
			if ( this.degStart.length == 1 ) {
				return this.degEnd[0];
			}
			return this.degEnd[1];
		}
		
		public float getMaxAngleCW() {
			return this.degStart[0];
		}
		
		private float normalize(float value) {
			while ( value >= 360 ) {
				value -= 360;
			}
			while ( value < 0 ) {
				value += 360;
			}
			return value;
		}
		
		public boolean isInRange(float value) 
		{
			value = normalize(value);

			for ( int i = 0 ; i < degStart.length ; i++ ) 
			{
				if ( value >= degStart[i] && value <= degEnd[i] ) {
					return true;
				}
			}
			return false;
		}
		
		public float clamp(float value) 
		{
			if ( isInRange( value ) ) {
				return value;
			}
			
			value = normalize(value);
			
			float bestValue=value;
			float bestDelta=Integer.MAX_VALUE;
			for ( int i = 0 ; i < degStart.length ; i++ ) 
			{
				float d1 = Math.abs( value - degStart[i] );
				float d2 = Math.abs( value - degEnd[i] );
				if ( d1 < d2 ) 
				{
					if ( d1 < bestDelta ) {
						bestValue = degStart[i];
						bestDelta = d1;
					}
				} 
				else 
				{
					if ( d2 < bestDelta ) {
						bestValue = degEnd[i];
						bestDelta = d2;
					}
				}
			}
			System.out.println("Clamping "+value+" to "+bestValue);
			return bestValue;
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
		float newValue = this.orientationDegrees + degreesDelta;
		if ( range.isInRange( newValue ) ) {
			setOrientation( newValue );
			return;
		}
		
		if ( degreesDelta >= 0 ) 
		{
			// rotate counter-clockwise
			float clampedValue = range.getMaxAngleCCW();
			System.out.println("Illegal rotation by "+degreesDelta+" , current angle is "+this.orientationDegrees+" ,"
					+ ", result "+newValue+" is not in range "+range+", clamping to "+clampedValue );
			setOrientation( clampedValue  );
			return;
		} 
		// rotate clockwise
		float clampedValue = range.getMaxAngleCW();
		System.out.println("Illegal rotation by "+degreesDelta+" , current angle is "+this.orientationDegrees+" ,"
				+ ", result "+newValue+" is not in range "+range+", clamping to "+clampedValue);		
		setOrientation( clampedValue);
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
