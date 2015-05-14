package de.codesourcery.inversek;

import com.badlogic.gdx.math.Vector2;

public class Bone extends Node
{
	public float length;
	
	public final Joint jointA;
	public final Joint jointB;
	
	public final Vector2 start = new Vector2();
	public final Vector2 end = new Vector2();
	
	public Bone(String name,Joint jointA, Joint jointB,float length) {
		super(name,NodeType.BONE);
		this.length = length;
		this.jointA = jointA;
		this.jointB = jointB;
	}
	
	public void forwardKinematics() 
	{
		start.set( jointA.radius + Model.JOINT_BONE_GAP , 0 );
		
		final float orientationDegrees = jointA.getSumOrientationDegrees();
		
		start.rotate( orientationDegrees );
		start.add( jointA.position );
		
		final Vector2 tmp = new Vector2( length , 0 );
		tmp.rotate( orientationDegrees );
		tmp.add( start );
		end.set( tmp );
		
		if ( jointB != null ) 
		{
			tmp.set( length + jointB.radius + Model.JOINT_BONE_GAP , 0 );
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
