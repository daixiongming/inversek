package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class KinematicsChain implements Iterable<Node<?>>, IMathSupport
{
	private final List<Joint> joints = new ArrayList<>();
	private final List<Bone> bones = new ArrayList<>();

	public KinematicsChain() {
	}

	public KinematicsChain(KinematicsChain other) 
	{
		final IdentityHashMap<Joint,Joint> jointMap = new IdentityHashMap<>();
		other.joints.forEach( joint -> 
		{ 
			final Joint copy = joint.createCopy() ;
			this.joints.add( copy );
			jointMap.put( joint , copy ); 
		});

		other.bones.forEach( bone -> 
		{
			Joint jointA = jointMap.get( bone.jointA );
			Joint jointB = jointMap.get( bone.jointB );
			final Bone copy = bone.createCopy( jointA , jointB );
			this.bones.add( copy );
			
			if ( jointA != null ) {
				jointA.successor = copy;
			}
			if ( jointB != null ) {
				jointB.predecessor = copy;
			}
		});
	}

	public KinematicsChain createCopy() {
		return new KinematicsChain(this);
	}

	@FunctionalInterface
	public interface INodeVisitor {
		public boolean visit(Node<?> n);
	}

	@FunctionalInterface
	public interface IJointVisitor {
		public boolean visit(Joint n);
	}
	
	@FunctionalInterface
	public interface IBoneVisitor {
		public boolean visit(Bone n);
	}
	
	public void visit(INodeVisitor visitor) 
	{
		for ( Joint j : joints ) {
			visitor.visit( j );
		}
		for (Bone b : bones ) {
			visitor.visit(b);
		}
	}
	
	public List<Joint> getJoints() {
		return this.joints;
	}
	
	public List<Bone> getBones() {
		return this.bones;
	}
	
	public boolean visitJoints(IJointVisitor visitor) 
	{
		for ( Joint j : joints ) {
			if ( ! visitor.visit( j ) ) {
				return false;
			}
		}
		return true;
	}
	
	public boolean visitBones(IBoneVisitor visitor) 
	{
		for ( Bone j : bones ) {
			if ( ! visitor.visit( j ) ) {
				return false;
			}
		}		
		return true;
	}

	public Stream<Node<?>> stream() {
		return StreamSupport.stream( this.spliterator(), false);		
	}
	
	public Joint getJointByID(String id) 
	{
		for ( Joint j : joints ) {
			if ( id.equals( j.getId() ) ) {
				return j;
			}
		}
		throw new NoSuchElementException("Unknown joint ID '"+id+"'");
	}
	
	public Bone getBoneByID(String id) 
	{
		for ( Bone j : bones) {
			if ( id.equals( j.getId() ) ) {
				return j;
			}
		}
		throw new NoSuchElementException("Unknown bone ID '"+id+"'");
	}

	public Iterator<Node<?>> iterator() 
	{
		return new Iterator<Node<?>>() 
				{
			private List<? extends Node<?>> currentCollection = joints;
			private Iterator<? extends Node<?>> it = currentCollection.iterator();

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
			public Node<?> next() 
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

	public Joint addJoint(String id,float orientation) 
	{
		if ( joints.stream().anyMatch( j -> j.getId().equals( id ) ) ) {
			throw new IllegalArgumentException("Duplicate joint '"+id+"'");
		}
		final Joint j = new Joint(id,Constants.JOINT_RADIUS,orientation);
		this.joints.add( j );
		return j;
	}

	public Bone addBone(String id,Joint a,Joint b,float length) 
	{
		final Bone bone = new Bone(id,a,b,length);
		addBone( bone );
		return bone;
	}
	
	public void addBone(Bone bone) 
	{
		if ( bones.stream().anyMatch( j -> j.getId().equals( bone.getId() ) ) ) {
			throw new IllegalArgumentException("Duplicate bone '"+bone.getId()+"'");
		}		
		bones.add( bone );
		bone.jointA.setSuccessor( bone );
		if ( bone.jointB != null ) {
			bone.jointB.setPrecessor( bone );
		}
	}

	public Joint getRootJoint() {
		for (Joint j : joints ) {
			if ( ! j.hasPredecessor() ) {
				return j;
			}
		}
		throw new RuntimeException("Internal error,no root joint?");
	}
	
	public Bone getEndBone() {
		for ( Bone b : bones ) {
			if ( b.jointB == null ) {
				return b;
			}
		}
		throw new RuntimeException("Either no bones or all bones end with a joint ?");
	}
	
	public void applyForwardKinematics() 
	{
		getRootJoint().successor.forwardKinematics();
	}	
	
	public void setRandomJointPositions(Random rnd) 
	{
		joints.forEach( joint -> joint.setRandomOrientation( rnd ) );
	}
	
	public void syncWithBox2d() {
		
		for ( Joint j : getJoints() ) 
		{
			float modelDeg = j.getOrientationDegrees();
			final float jointAngleInRad = j.getBody().getJointAngle();
			final float jointAngleInDeg = radToDeg( j.getBody().getJointAngle() );
			float actual = box2dAngleToDeg( jointAngleInRad );
			System.out.println("Syncing joint "+j+", model: "+modelDeg+" -> actual: "+actual+" (rad: "+jointAngleInRad+" -> "+jointAngleInDeg+")");
			j.setOrientation( actual );
		}
		getRootJoint().successor.forwardKinematics();
	}
}