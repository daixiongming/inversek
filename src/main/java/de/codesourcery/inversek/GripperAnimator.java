package de.codesourcery.inversek;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;

import de.codesourcery.inversek.WorldModel.Ball;

public class GripperAnimator implements ITickListener , IContactCallback {

	private static final float GRIPPER_SPEED = 0.05f;
	private static final float EPSILON = 0.001f;
	private static final float ON_CONTACT_DELAY_SECONDS = 1.0f;
	
	protected static enum Direction { OPENING,CLOSING }
	
	private final WorldModel worldModel;
	private final Gripper gripper;
	private final Direction direction;
	private final float desiredOpenPercentage;
	
	private boolean lowerJointAtDestination;
	private boolean upperJointAtDestination;
	
	private boolean contactCallbackRegistered;
	
	private boolean lowerClawContact;
	private boolean upperClawContact;
	
	private int clawStoppedCount = 0;
	private float elapsedStopDelaySeconds = 0;
	
	public GripperAnimator(WorldModel worldModel, Gripper gripper,float open) 
	{
		if ( open < 0f || open > 1.0f ) {
			throw new IllegalArgumentException("Illegal open factor: "+open);
		}
		gripper.updateOpenPercentage();
		this.direction = gripper.getOpenPercentage() > open ? Direction.CLOSING : Direction.OPENING;
		this.gripper = gripper;
		this.desiredOpenPercentage = open;
		this.worldModel = worldModel;
		System.out.println( ( isOpening() ? "OPENING":"CLOSING" )+ " claw...");
	}
	
	public boolean hasFinished() {
		return lowerJointAtDestination && upperJointAtDestination;
	}
	
	public void emergencyStop() 
	{
		lowerJointAtDestination = true;
		upperJointAtDestination = true;
		
		gripper.getLowerJoint().setMotorSpeed( 0 );
		gripper.getUpperJoint().setMotorSpeed( 0 );
		cleanUp();
	}
	
	private boolean isClosing() {
		return this.direction == Direction.CLOSING;
	}
	
	private boolean isOpening() {
		return this.direction == Direction.OPENING;
	}
	
	private void cleanUp() 
	{
		if ( isClosing() ) {
			worldModel.removeContractCallback( this );
		}
	}
	
	@Override
	public boolean tick(float deltaSeconds) 
	{
		if ( lowerJointAtDestination && upperJointAtDestination ) 
		{
			cleanUp();
			return false;
		}
		
		if ( ! contactCallbackRegistered ) 
		{
			if ( isClosing() ) {
				worldModel.addContactCallback( this );
			}
			contactCallbackRegistered = true;
		}
		
		if ( clawStoppedCount > 0 ) 
		{
			elapsedStopDelaySeconds += deltaSeconds;
		}
		
		final PrismaticJoint lowerJoint = gripper.getLowerJoint();
		final PrismaticJoint upperJoint = gripper.getUpperJoint();

		final float lowerOpenPercentage = gripper.getLowerClawOpenPercentage();
		final float upperOpenPercentage = gripper.getUpperClawOpenPercentage();
		
		gripper.updateOpenPercentage();
		
		boolean lowerClawReachedDestination = lowerJointAtDestination || Math.abs( lowerOpenPercentage - desiredOpenPercentage ) <= EPSILON;
		if ( ! lowerClawContact && ! lowerClawReachedDestination ) 
		{
			float speed = desiredOpenPercentage < lowerOpenPercentage ? 1 : -1; 
			lowerJoint.setMotorSpeed( speed * GRIPPER_SPEED );
		} 
		else 
		{
			if ( lowerClawReachedDestination || lowerClawContact && (clawStoppedCount == 0  || clawStoppedCount == 1 && elapsedStopDelaySeconds >= ON_CONTACT_DELAY_SECONDS) ) 
			{
				if ( ! lowerJointAtDestination ) 
				{
					if ( lowerClawReachedDestination ) {
						System.out.println("*** Lower claw reached destination percentage ***");
					} else if ( clawStoppedCount == 0 ) {
						System.out.println("*** Lower claw hit ball first, stopping");
					} else {
						System.out.println("*** Lower claw hit ball second , stopping at "+clawStoppedCount+" , delay: "+elapsedStopDelaySeconds+" s");
					}
					lowerJoint.setMotorSpeed(0);
					clawStoppedCount++;
					lowerJointAtDestination = true;
				}
			}
		}
		
		final boolean upperClawReachedDestination = upperJointAtDestination || Math.abs( upperOpenPercentage - desiredOpenPercentage ) <= EPSILON; 
		if ( ! upperClawContact && ! upperClawReachedDestination ) 
		{
			float speed = desiredOpenPercentage < upperOpenPercentage ? 1 : -1; 
			upperJoint.setMotorSpeed( speed * GRIPPER_SPEED );
		} 
		else 
		{
			if ( upperClawReachedDestination || upperClawContact && (clawStoppedCount == 0  || clawStoppedCount == 1 && elapsedStopDelaySeconds >= ON_CONTACT_DELAY_SECONDS) ) 
			{
				if ( ! upperJointAtDestination ) 
				{
					if ( upperClawReachedDestination ) {
						System.out.println("*** Upper claw reached destination percentage ***");
					} else if ( clawStoppedCount == 0 ) {
						System.out.println("*** Upper claw hit ball first, stopping");
					} else {
						System.out.println("*** Upper claw hit ball second , stopping at "+clawStoppedCount+" , delay: "+elapsedStopDelaySeconds+" s");
					}					
					upperJoint.setMotorSpeed(0);
					clawStoppedCount++;
					upperJointAtDestination = true;
				}
			}
		}		
		
		if ( lowerJointAtDestination && upperJointAtDestination ) 
		{
			System.out.println("Gripper arrived at destination: lower: "+lowerOpenPercentage+" % , upper: "+upperOpenPercentage+" %");
			cleanUp();
			return false;
		}
		return true;
	}

	@Override
	public boolean endContact(Contact contact) {
		return true;
	}

	@Override
	public boolean beginContact(Contact contact) 
	{
		if ( ! contact.isTouching() ) {
			return true;
		}
		
		final Fixture fixtureA = contact.getFixtureA();
		final Fixture fixtureB = contact.getFixtureB();
		
		Fixture claw = null;
		if ( isBall( fixtureA ) ) 
		{
			claw = isClaw( fixtureB ) ? fixtureB : null;
		} 
		else if ( isBall( fixtureB ) ) 
		{
			claw = isClaw( fixtureA ) ? fixtureA : null;
		}
		
		if ( claw == null ) {
			System.out.println("*** Ignored contact "+contact.getFixtureA().getUserData()+" vs. "+contact.getFixtureB().getUserData()+" ***");
			return true;
		}
		
		if ( isLowerClaw( claw ) ) 
		{
			if ( ! lowerClawContact ) 
			{
				System.out.println("*** lower claw touched ball *** normal = "+contact.getWorldManifold().getNormal());
				lowerClawContact = true;
			}
		} 
		else if ( isUpperClaw( claw ) ) 
		{
			if ( ! upperClawContact ) 
			{
				System.out.println("*** Upper claw touched ball *** normal = "+contact.getWorldManifold().getNormal());
				upperClawContact = true;
			}
		}
		return true;
	}
	
	private boolean isBall(Fixture fixture) 
	{
		return fixture.getUserData() instanceof Ball;
	}
	
	private boolean isClaw(Fixture fixture) {
		return isLowerClaw( fixture ) || isUpperClaw(fixture);
	}
	
	private boolean isLowerClaw(Fixture fixture) 
	{
		if ( fixture.getUserData() instanceof Gripper) 
		{
			return fixture.getBody() == gripper.getLowerClawBody();
		}
		return false;
	}
	
	private boolean isUpperClaw(Fixture fixture) 
	{
		if ( fixture.getUserData() instanceof Gripper) 
		{
			return fixture.getBody() == gripper.getUpperClawBody();
		}
		return false;
	}	
}