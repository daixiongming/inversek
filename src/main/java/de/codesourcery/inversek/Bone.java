package de.codesourcery.inversek;

import com.badlogic.gdx.math.Vector2;

public class Bone extends Node<com.badlogic.gdx.physics.box2d.Body>
{
	public float length;
	
	public final Joint jointA;
	public final Joint jointB;
	
	public final Vector2 start = new Vector2();
	public final Vector2 end = new Vector2();
	
	public Bone(String name,Joint jointA, Joint jointB,float length) {
		super(null,name,NodeType.BONE);
		this.length = length;
		this.jointA = jointA;
		this.jointB = jointB;
	}
	
	protected Bone(Bone that, Joint jointA, Joint jointB) 
	{
		super(that.getBody() , that.getId(),NodeType.BONE);
		this.start.set( that.start );
		this.end.set( that.end );
		this.length = that.length;
		this.jointA = jointA;
		this.jointB = jointB;		
	}

	public Bone createCopy(Joint jointA, Joint jointB) {
		return new Bone(this,jointA,jointB);
	}
	
	public Vector2 getCenter() {
		return start.cpy().add( end ).scl( 0.5f );
	}
	
	public Vector2 getPositioningEnd() {
		return end;
	}
	
	public void forwardKinematics() 
	{
		start.set( jointA.radius , 0 );
		
		final float orientationDegrees = jointA.getSumOrientationDegrees();
		
		start.rotate( orientationDegrees );
		start.add( jointA.position );
		
		final Vector2 tmp = new Vector2( length , 0 );
		tmp.rotate( orientationDegrees );
		tmp.add( start );
		end.set( tmp );
		
		if ( jointB != null ) 
		{
			tmp.set( length + jointB.radius , 0 );
			tmp.rotate( orientationDegrees );
			tmp.add( start );
			jointB.position.set( tmp );
			if ( jointB.successor != null ) 
			{
				jointB.successor.forwardKinematics();
			}
		}
	}
}
