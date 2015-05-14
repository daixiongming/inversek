package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Model implements Iterable<Node>
{
	public static final float JOINT_BONE_GAP = 3;
	public static final float JOINT_RADIUS = 10;
	
	private final List<Joint> joints = new ArrayList<>();
	private final List<Bone> bones = new ArrayList<>();
	
	public Model() {
	}
	
	@FunctionalInterface
	public interface INodeVisitor {
		public boolean visit(Node n);
	}
	
	public void visit(INodeVisitor visitor) 
	{
		for ( Joint j : joints ) {
			if ( ! visitor.visit( j ) ) {
				return;
			}
		}
		for ( Bone j : bones ) {
			if ( ! visitor.visit( j ) ) {
				return;
			}
		}		
	}
	
	public Stream<Node> stream() {
		return StreamSupport.stream( this.spliterator(), false);		
	}
	
	public Iterator<Node> iterator() 
	{
 		return new Iterator<Node>() 
 		{
 			private List<? extends Node> currentCollection = joints;
 			private Iterator<? extends Node> it = currentCollection.iterator();

			@Override
			public boolean hasNext() 
			{
				if ( ! it.hasNext() ) 
				{
					if ( currentCollection == joints ) {
						currentCollection = bones;
						it = currentCollection.iterator();
					}
					return it.hasNext();
				}
				return true;
			}

			@Override
			public Node next() 
			{
				if ( ! it.hasNext() ) 
				{
					if ( currentCollection == bones ) {
						throw new NoSuchElementException("Iterator already at EOF");						
					}
					currentCollection = bones;
					it = currentCollection.iterator();
				}
				return it.next();
			}
		};
	}
	
	public Joint addJoint(String name,float orientation) 
	{
		final Joint j = new Joint(name,JOINT_RADIUS,orientation);
		this.joints.add( j );
		return j;
	}

	public void addBone(String name,Joint a,Joint b,float length) 
	{
		final Bone bone = new Bone(name,a,b,length);
		bones.add( bone );
		a.setSuccessor( bone );
		if ( b != null ) {
			b.setPrecessor( bone );
		}
	}
	
	public void applyForwardKinematics() 
	{
		for ( Joint j : joints ) 
		{
			if ( ! j.hasPredecessor() ) {
				j.successor.forwardKinematics();
			}
		}
	}	
}