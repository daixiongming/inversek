package de.codesourcery.inversek;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;

public class Joint extends Node<RevoluteJoint> implements IMathSupport
{
	private float orientationDegrees = 0;
	private final Vector2 orientation = new Vector2();
	
	public final Vector2 position = new Vector2();
	
	public final float radius;
	
	public Bone successor;
	public Bone predecessor;
	
	public MovementRange range = new MovementRange(0,360);
	
	private Joint(Joint other,Bone predecessor,Bone successor) 
	{
		super(other.getBody() , other.getId(),NodeType.JOINT);
		this.orientationDegrees = other.orientationDegrees;
		this.orientation.set( other.orientation );
		this.position.set( other.position );
		this.radius = other.radius;
		this.successor = successor;
		this.predecessor = predecessor;
		this.range = other.range;
	}
	
	public Joint createCopy() {
		return new Joint(this,null,null);
	}
	
	protected static final class Interval {
		
		public final float start;
		public final float end;
		
		public Interval(float start, float end) 
		{
			if ( start > end ) {
				throw new IllegalArgumentException("start must be <= end , start: "+start+", end: "+end);
			}
			this.start = start;
			this.end = end;
		}
		
		public boolean contains(float f) 
		{
			return start <= f && f <= end;
		}
		
		@Override
		public String toString() {
			return "["+start+","+end+"]";
		}

		public float getRandomValue(Random rnd) 
		{
			final float range = end-start;
			return start+rnd.nextFloat()*range;
		}
	}
	
	public static final class MovementRange implements IMathSupport {
		
		public final Interval[] intervals;
		
		public MovementRange(float degStart,float degEnd) 
		{
			if ( degStart <= degEnd ) {
				this.intervals = new Interval[] { new Interval( degStart , degEnd ) };
			} else {
				this.intervals = new Interval[] { 
						new Interval( degStart , 360 ),
						new Interval( 0 , degEnd )
				}; 
			}
		}
		
		public float getMinimumAngle() {
			return intervals[0].start;
		}
		
		public float getMaximumAngle() {
			return intervals.length == 1 ? intervals[0].end : intervals[1].end;
		}
		
		public MovementRange(MovementRange other) 
		{
			this.intervals = new Interval[ other.intervals.length ];
			System.arraycopy( other.intervals , 0 , this.intervals , 0 , this.intervals.length );
		}
		
		public MovementRange createCopy() {
			return new MovementRange(this);
		}
		
		@Override
		public String toString() {
			return Arrays.stream( intervals ).map( Interval::toString ).collect( Collectors.joining(",") );
		}
		
		public float getMaxValidAngleCCW() {
			if ( this.intervals.length == 1 ) {
				return this.intervals[0].end;
			}
			return this.intervals[1].end;
		}
		
		public float getMaxValidAngleCW() {
			return this.intervals[0].start;
		}
		
		public boolean isInRange(float value) 
		{
			value = normalizeAngleInDeg(value);

			for ( int i = 0 ; i < intervals.length ; i++ ) 
			{
				if ( intervals[i].contains( value ) ) {
					return true;
				}
			}
			return false;
		}
		
		public float clamp(float value) 
		{
			if ( isInRange( value ) ) 
			{
				if ( value < 0 || value > 360 ) {
					return normalizeAngleInDeg(value);
				}
				return value;
			}
			
			value = normalizeAngleInDeg(value);
			
			float bestValue=value;
			float bestDelta=Integer.MAX_VALUE;
			for ( int i = 0 ; i < intervals.length ; i++ ) 
			{
				float d1 = Math.abs( value - intervals[i].start );
				float d2 = Math.abs( value - intervals[i].end );
				if ( d1 < d2 ) 
				{
					if ( d1 < bestDelta ) {
						bestValue = intervals[i].start;
						bestDelta = d1;
					}
				} 
				else 
				{
					if ( d2 < bestDelta ) {
						bestValue = intervals[i].end;
						bestDelta = d2;
					}
				}
			}
			System.out.println("Clamping "+value+" to "+bestValue);
			return bestValue;
		}

		public float getRandomValue(Random rnd) 
		{
			final int idx = rnd.nextInt( intervals.length );
			return intervals[idx].getRandomValue(rnd);
		}
	}
	
	public Joint(String name,float radius,float orientation) 
	{
		super(null,name,NodeType.JOINT);
		if ( radius < 0 ) {
			throw new IllegalArgumentException("radius needs to be >= 0,was: "+radius);
		}
		this.radius = radius;
		this.range = new MovementRange(0,360);
		setOrientation(orientation);
	}
	
	public void setRange(MovementRange range) {
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
	
	public void syncWithBox2d() {
		setOrientation( getBox2dOrientationDegrees() );
	}
	
	public void setOrientation(float degrees) 
	{
		orientationDegrees = range.clamp( degrees );
		orientation.set(1,0).rotate( degrees );
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
			// caller wants to rotate counter-clockwise
			float clampedValue = range.getMaxValidAngleCCW();
			if ( Main.DEBUG ) {
				System.out.println("Illegal rotation by "+degreesDelta+" , current angle is "+this.orientationDegrees+" ,"
					+ ", result "+newValue+" is not in range "+range+", clamping to "+clampedValue );
			}
			setOrientation( clampedValue  );
			return;
		} 
		// caller wants to rotate clockwise
		float clampedValue = range.getMaxValidAngleCW();
		if ( Main.DEBUG ) {
			System.out.println("Illegal rotation by "+degreesDelta+" , current angle is "+this.orientationDegrees+" ,"
				+ ", result "+newValue+" is not in range "+range+", clamping to "+clampedValue);
		}
		setOrientation( clampedValue);
	}	
	
	public float getOrientationDegrees() {
		return orientationDegrees;
	}
	
	public float getSumOrientationDegrees() 
	{
		return predecessor == null ? orientationDegrees : orientationDegrees + predecessor.jointA.getSumOrientationDegrees();  
	}
	
	public float getBox2dOrientationDegrees() 
	{
		float angle = radToDeg( getBody().getJointAngle() );
		if ( angle < 0 ) {
			angle = 360+angle;
		}
		return normalizeAngleInDeg( angle );
	}
	
	public float getSumOrientationDegreesBox2d() 
	{
		return predecessor == null ? getBox2dOrientationDegrees() : getBox2dOrientationDegrees() + predecessor.jointA.getSumOrientationDegreesBox2d();  
	}	
	
	public Vector2 getOrientation() {
		return orientation;
	}
	
	@Override
	public String toString() {
		return "Joint "+getId()+" , angle "+this.orientationDegrees+", "+this.range;
	}
	
	public void setRandomOrientation(Random rnd) {
		setOrientation( range.getRandomValue(rnd) );
	}
}
