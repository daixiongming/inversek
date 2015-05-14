package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.badlogic.gdx.math.Vector2;

public class Model implements Iterable<Node>
{
	public static final float JOINT_BONE_GAP = 3;
	public static final float JOINT_RADIUS = 10;
	
    // Set an epsilon value to prevent division by small numbers.
   private static final double EPSILON = 0.0001; 
   
   private static final double DESIRED_ARRIVAL_DST = 1;
   
   protected static final int FAILURE_RETRY_COUNT = 5;
   
   private static final double MIN_CHANGE  = 0.1;
	
	private final List<Joint> joints = new ArrayList<>();
	private final List<Bone> bones = new ArrayList<>();
	
	protected static enum Outcome {
		PROCESSING,SUCCESS,FAILURE;
	}
	
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

	public Bone addBone(String name,Joint a,Joint b,float length) 
	{
		final Bone bone = new Bone(name,a,b,length);
		bones.add( bone );
		a.setSuccessor( bone );
		if ( b != null ) {
			b.setPrecessor( bone );
		}
		return bone;
	}
	
	public void applyInverseKinematics(Bone bone,Vector2 desiredPosition) 
	{
		Outcome result = Outcome.FAILURE;
		int iterations = 0;
		int retries = FAILURE_RETRY_COUNT;
		while ( true ) 
		{
			result = singleIteration( bone , desiredPosition );
			iterations++;
			if ( result == Outcome.SUCCESS ) {
				break;
			}
			if ( result == Outcome.FAILURE ) {
				retries--;
				if ( retries < 0 ) {
					break;
				}
			} else {
				retries = FAILURE_RETRY_COUNT;
			}
		}
		System.out.println("RESULT: "+result+" (iterations: "+iterations+")");
	}
	
	public Outcome singleIteration(Bone bone,Vector2 desiredPosition) 
	{
		// locate root joint for this bone
		
		Joint currentJoint = bone.jointA;
		
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
	        currentJoint.addOrientation( (float) rotDeg );
	        
			// apply delta
			System.out.println("Adjusting "+currentJoint+" by "+rotDeg+" degrees");
			
			// process next joint
			if ( currentJoint.successor != null  ) {
				// recalculate positions
				currentJoint.successor.forwardKinematics();				
			}
			
			// check for termination
			if ( bone.end.dst( desiredPosition ) <= DESIRED_ARRIVAL_DST ) {
				System.out.println("Arrived at destination");
				return Outcome.SUCCESS;
			}			
			
			if ( currentJoint.predecessor != null ) 
			{
				currentJoint = currentJoint.predecessor.jointA;
			} else {
				currentJoint = null;
			}
		} 
		
		/*
	    // Get the vector from the current bone to the end effector position.
	        double curToEndX = endX - worldBones[boneIdx].x;
	        double curToEndY = endY - worldBones[boneIdx].y;
	        double curToEndMag = Math.Sqrt( curToEndX*curToEndX + curToEndY*curToEndY );
	  
	        // Get the vector from the current bone to the target position.
	        double curToTargetX = targetX - worldBones[boneIdx].x;
	        double curToTargetY = targetY - worldBones[boneIdx].y;
	        double curToTargetMag = Math.Sqrt(   curToTargetX*curToTargetX + curToTargetY*curToTargetY );
	  
	        // Get rotation to place the end effector on the line from the current
	        // joint position to the target postion.
	        double cosRotAng;
	        double sinRotAng;
	        double endTargetMag = (curToEndMag*curToTargetMag);
	        if( endTargetMag <= epsilon )
	        {
	            cosRotAng = 1;
	            sinRotAng = 0;
	        }
	        else
	        {
	            cosRotAng = (curToEndX*curToTargetX + curToEndY*curToTargetY) / endTargetMag;
	            sinRotAng = (curToEndX*curToTargetY - curToEndY*curToTargetX) / endTargetMag;
	        }
	  
	        // Clamp the cosine into range when computing the angle (might be out of range
	        // due to floating point error).
	        double rotAng = Math.Acos( Math.Max(-1, Math.Min(1,cosRotAng) ) );
	        if( sinRotAng < 0.0 )
	            rotAng = -rotAng;
	  
	        // Rotate the end effector position.
	        endX = worldBones[boneIdx].x + cosRotAng*curToEndX - sinRotAng*curToEndY;
	        endY = worldBones[boneIdx].y + sinRotAng*curToEndX + cosRotAng*curToEndY;
	  
	        // Rotate the current bone in local space (this value is output to the user)
	        bones[boneIdx].angle = SimplifyAngle( bones[boneIdx].angle + rotAng );
	  
	        // Check for termination
	        double endToTargetX = (targetX-endX);
	        double endToTargetY = (targetY-endY);
	        if( endToTargetX*endToTargetX + endToTargetY*endToTargetY <= arrivalDistSqr )
	        {
	            // We found a valid solution.
	            return CCDResult.Success;
	        }
	  
	        // Track if the arc length that we moved the end effector was
	        // a nontrivial distance.
	        if( !modifiedBones && Math.Abs(rotAng)*curToEndMag > trivialArcLength )
	        {
	            modifiedBones = true;
	        }
	    }
	  
	    // We failed to find a valid solution during this iteration.
	    if( modifiedBones )
	        return CCDResult.Processing;
	    else
	        return CCDResult.Failure;	 
					 */		
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
}