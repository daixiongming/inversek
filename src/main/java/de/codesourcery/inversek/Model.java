package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.badlogic.gdx.math.Vector2;

public class Model implements Iterable<Node>
{
	public static final float JOINT_BONE_GAP = 3;
	public static final float JOINT_RADIUS = 10;

	private static final double EPSILON = 0.0001; 

	private static final double DESIRED_ARRIVAL_DST = 1;
	
	protected static final int RANDOM_RETRIES = 10;

	protected static final int FAILURE_RETRY_COUNT = 50;

	private static final int MAX_ITERATIONS = 5000;

	private static final double MIN_CHANGE  = 0.01;
	
	private final Random rnd = new Random(System.currentTimeMillis());

	private final List<Joint> joints = new ArrayList<>();
	private final List<Bone> bones = new ArrayList<>();

	public static enum Outcome {
		PROCESSING,SUCCESS,FAILURE;
	}

	public Model() {
	}

	public Model(Model other) 
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

	public Model createCopy() {
		return new Model(this);
	}

	@FunctionalInterface
	public interface INodeVisitor {
		public boolean visit(Node n);
	}

	public void visit(INodeVisitor visitor) 
	{
		if ( visitJoints( visitor ) ) 
		{
			visitBones( visitor );
		}
	}
	
	public boolean visitJoints(INodeVisitor visitor) 
	{
		for ( Joint j : joints ) {
			if ( ! visitor.visit( j ) ) {
				return false;
			}
		}
		return true;
	}
	
	public boolean visitBones(INodeVisitor visitor) 
	{
		for ( Bone j : bones ) {
			if ( ! visitor.visit( j ) ) {
				return false;
			}
		}		
		return true;
	}

	public Stream<Node> stream() {
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

	public Joint addJoint(String id,float orientation) 
	{
		if ( joints.stream().anyMatch( j -> j.getId().equals( id ) ) ) {
			throw new IllegalArgumentException("Duplicate joint '"+id+"'");
		}
		final Joint j = new Joint(id,JOINT_RADIUS,orientation);
		this.joints.add( j );
		return j;
	}

	public Bone addBone(String id,Joint a,Joint b,float length) 
	{
		if ( bones.stream().anyMatch( j -> j.getId().equals( id ) ) ) {
			throw new IllegalArgumentException("Duplicate bone '"+id+"'");
		}
		
		final Bone bone = new Bone(id,a,b,length);
		bones.add( bone );
		a.setSuccessor( bone );
		if ( b != null ) {
			b.setPrecessor( bone );
		}
		return bone;
	}

	public boolean applyInverseKinematics(Bone bone,Vector2 desiredPosition) 
	{
		for ( int i = RANDOM_RETRIES ; i > 0 ; i-- ) 
		{
			if ( doApplyInverseKinematics(bone, desiredPosition) ) {
				return true;
			}
			setRandomJointPositions();
		} 
		return false;
	}
	
	private boolean doApplyInverseKinematics(Bone bone,Vector2 desiredPosition) 
	{
		Outcome result = Outcome.FAILURE;
		int iterations = MAX_ITERATIONS;
		int retries = FAILURE_RETRY_COUNT;
		do 
		{
			result = singleIteration( bone , desiredPosition );
			iterations--;

			if ( result == Outcome.SUCCESS ) {
				break;
			}

			if ( result == Outcome.FAILURE ) 
			{
				retries--;
				if ( retries < 0 ) {
					break;
				}
			} else {
				retries = FAILURE_RETRY_COUNT;
			}
		} while ( iterations > 0 );

		System.out.println("RESULT: "+result+" (iterations: "+(MAX_ITERATIONS - iterations )+",failure retries left: "+retries+")");
		return result.equals( Outcome.SUCCESS );
	}

	public Outcome singleIteration(final Bone bone,Vector2 desiredPosition) 
	{
		Joint currentJoint = bone.jointA;
		
		/* Code heavily inspired by http://www.ryanjuckett.com/programming/cyclic-coordinate-descent-in-2d/
		 */

		final float initialDistance = bone.end.dst(desiredPosition);
		while ( true ) 
		{
			final Bone currentBone = currentJoint == null ? null : currentJoint.successor;
			if ( currentBone == null ) 
			{
				// check for termination
				final float currentDst = bone.end.dst( desiredPosition ); 				
				if ( currentDst <= DESIRED_ARRIVAL_DST ) {
					System.out.println("Arrived at destination");
					return Outcome.SUCCESS;
				}	

				if ( Math.abs( currentDst - initialDistance ) >= MIN_CHANGE ) {
					return Outcome.PROCESSING;
				}
				return Outcome.FAILURE;				
			}

			// Get the vector from the current bone to the end effector position.			
			final Vector2 curToEnd = bone.end.cpy().sub( currentBone.getCenter() );
			final double curToEndMag = curToEnd.len();

			// Get the vector from the current bone to the target position.
			final Vector2 curToTarget = desiredPosition.cpy().sub( currentBone.getCenter() );
			final double curToTargetMag = curToTarget.len();

			// Get rotation to place the end effector on the line from the current
			// joint position to the target postion.	
			final double cosRotAng;
			final double sinRotAng;
			final double endTargetMag = (curToEndMag*curToTargetMag);
			if( endTargetMag <= EPSILON )
			{
				cosRotAng = 1;
				sinRotAng = 0;
			}
			else
			{
				cosRotAng = (curToEnd.x*curToTarget.x + curToEnd.y*curToTarget.y) / endTargetMag;
				sinRotAng = (curToEnd.x*curToTarget.y - curToEnd.y*curToTarget.x) / endTargetMag;
			}	

			// Clamp the cosine into range when computing the angle (might be out of range
			// due to floating point error).
			double rotAng = Math.acos( Math.max(-1, Math.min(1,cosRotAng) ) );
			if( sinRotAng < 0.0 ) {
				rotAng = -rotAng;	
			}

			// apply rotation

			final double rotDeg = rotAng * (180.0/Math.PI); // convert rad to deg
			if ( Main.DEBUG ) {
				System.out.println("Adjusting "+currentJoint+" by "+rotDeg+" degrees");
			}
			currentJoint.addOrientation( (float) rotDeg );

			// update bone positions
			if ( currentJoint.successor != null  ) {
				currentJoint.successor.forwardKinematics();				
			}

			// check for termination
			if ( bone.end.dst( desiredPosition ) <= DESIRED_ARRIVAL_DST ) {
				System.out.println("Arrived at destination");
				return Outcome.SUCCESS;
			}			

			// process next joint
			if ( currentJoint.predecessor != null ) 
			{
				currentJoint = currentJoint.predecessor.jointA;
			} else {
				currentJoint = null;
			}
		} 
	}

	public void applyForwardKinematics() 
	{
		for ( Joint j : joints ) 
		{
			// appply forward kinetics for every root joint (=joints without predecessors)
			if ( ! j.hasPredecessor() ) {
				j.successor.forwardKinematics();
			}
		}
	}	
	
	public void setRandomJointPositions() 
	{
		joints.forEach( joint -> joint.setRandomOrientation( rnd ) );
	}
}