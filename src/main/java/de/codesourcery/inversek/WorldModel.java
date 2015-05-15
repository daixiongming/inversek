package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.JointDef;
import com.badlogic.gdx.physics.box2d.JointDef.JointType;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;

public class WorldModel implements ITickListener 
{
	private static final float PHYSICS_TIMESTEP = 1/60f;
	private static final int VELOCITY_ITERATIONS = 6;
	private static final int POSITION_ITERATIONS = 2;
	
	private static final float BALL_RADIUS =10f;
	
	private static final float DENSITY = 1;
	
	private final World world;
	private float accumulator = 0;
	
	private final float floorY = 0;
	
	private final List<Ball> balls = new ArrayList<>(); 
	
	protected static final class Ball 
	{
		private final Body body;
		public final float radius;
		
		public Ball(Body body,float radius) {
			this.radius = radius;
			this.body = body;
		}
		
		public Vector2 getPosition() {
			return body.getPosition();
		}
	}
		
	public WorldModel() {
		world  = new World( new Vector2(0,-20) , true );
		setupFloorPlane();
	}
	
	public List<Ball> getBalls() {
		return balls;
	}
	
	public float getFloorY() {
		return floorY;
	}
	
	private void setupFloorPlane() 
	{
		final float floorThickness = 20;
		// Create our body definition
		BodyDef groundBodyDef =new BodyDef();  
		
		// Set its world position
		groundBodyDef.position.set(new Vector2(0, floorY-floorThickness+BALL_RADIUS));  

		// Create a body from the definition and add it to the world
		Body groundBody = world.createBody(groundBodyDef);  

		// Create a polygon shape
		final PolygonShape groundBox = new PolygonShape();  
		
		// Set the polygon shape as a box 
		// NOTE: setAsBox takes half-width and half-height as arguments !!
		groundBox.setAsBox(1000, floorThickness/2.0f );
		
		// Create a fixture from our polygon shape and add it to our ground body  
		groundBody.createFixture(groundBox, 0.0f); 
		
		// Clean up after ourselves
		groundBox.dispose();
	}
	
	public void addBall(float x,float y) 
	{
		// First we create a body definition
		final BodyDef bodyDef = new BodyDef();
		
		// We set our body to dynamic, for something like ground which doesn't move we would set it to StaticBody
		bodyDef.type = BodyType.DynamicBody;
		
		// Set our body's starting position in the world
		bodyDef.position.set(x, y);
		
		System.out.println("Added ball @ "+x+","+y);
		
		// Create our body in the world using our body definition
		final Body body = world.createBody(bodyDef);

		final Ball ball = new Ball( body , BALL_RADIUS );
		balls.add( ball );
		
		// Create a circle shape and set its radius to 6
		final CircleShape circle = new CircleShape();
		circle.setRadius( BALL_RADIUS );

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = circle;
		fixtureDef.density = DENSITY;
		fixtureDef.friction = 1.8f;
		fixtureDef.restitution = 0.3f; // Make it bounce a little bit

		// Create our fixture and attach it to the body
		final Fixture fixture = body.createFixture(fixtureDef);
		
		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		circle.dispose();
	}
	
	@Override
	public boolean tick(float deltaSeconds) 
	{
	    // fixed time step
	    // max frame time to avoid spiral of death (on slow devices)
	    float frameTime = Math.min(deltaSeconds, 0.25f);
	    accumulator += frameTime;
	    while (accumulator >= PHYSICS_TIMESTEP)
	    {
	        world.step(PHYSICS_TIMESTEP, VELOCITY_ITERATIONS , POSITION_ITERATIONS );
	        accumulator -= PHYSICS_TIMESTEP;
	    }
	    return true;
	}

	public void add(RobotArm arm) 
	{
		KinematicsChain chain=arm.getModel().getChains().get(0);
		for ( Bone b : chain.getBones() ) {
			registerBone( b );
		}
		
		for ( de.codesourcery.inversek.Joint j : chain.getJoints() ) 
		{
			registerJoint(arm,j);
		}
	}	
	
	public void registerBone(Bone bone) 
	{
		final float angleRad = bone.start.angleRad( bone.end );
		
		// First we create a body definition
		final BodyDef bodyDef = new BodyDef();
		
		// We set our body to dynamic, for something like ground which doesn't move we would set it to StaticBody
		bodyDef.type = BodyType.DynamicBody;
		
		// Set our body's starting position in the world
		bodyDef.position.set( bone.getCenter() );
		bodyDef.angle = angleRad;
		
		// Create our body in the world using our body definition
		final Body body = world.createBody(bodyDef);

		bone.setBody( body );
		
		// Create a circle shape and set its radius to 6
		final PolygonShape box = new PolygonShape();
		box.setAsBox( bone.length/2  , Bone.BONE_THICKNESS/2f, bone.getCenter() , angleRad);

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = box;
		fixtureDef.density = DENSITY;
		fixtureDef.friction = 0f;
		fixtureDef.restitution = 0f; // Make it bounce a little bit

		// Create our fixture and attach it to the body
		final Fixture fixture = body.createFixture(fixtureDef);
		
		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		box.dispose();		
	}
	
	public void registerJoint(RobotArm arm,de.codesourcery.inversek.Joint joint) 
	{
		final RevoluteJointDef def = new RevoluteJointDef();
		def.collideConnected=false;
		if ( joint.predecessor != null ) {
			def.bodyA = joint.predecessor.getBody();
			def.localAnchorA.set( joint.predecessor.length/2 , 0 );
		} else {
			def.bodyA = createRobotBase();
			arm.setBase( def.bodyA );
			def.localAnchorA.set( RobotArm.ROBOTBASE_WIDTH/2 , RobotArm.ROBOTBASE_HEIGHT ); 
		}
		def.bodyB = joint.successor.getBody();
		def.localAnchorB.set( -joint.successor.length/2 , 0 );
		def.enableLimit = true;
		float angleLimit = degToRad( convertAngle( joint.getOrientationDegrees() ) );
		def.lowerAngle = angleLimit;
		def.upperAngle = angleLimit;
		def.referenceAngle = 0;
		Joint j = world.createJoint(def);
	}
	
	private Body createRobotBase() 
	{
		// First we create a body definition
		final BodyDef bodyDef = new BodyDef();
		
		// We set our body to dynamic, for something like ground which doesn't move we would set it to StaticBody
		bodyDef.type = BodyType.StaticBody;
		
		// Set our body's starting position in the world
		bodyDef.position.set( 0, RobotArm.ROBOTBASE_HEIGHT/2 );
		
		// Create our body in the world using our body definition
		final Body body = world.createBody(bodyDef);

		// Create a circle shape and set its radius to 6
		final PolygonShape box = new PolygonShape();
		box.setAsBox( RobotArm.ROBOTBASE_WIDTH/2  , RobotArm.ROBOTBASE_HEIGHT/2 );

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = box;
		fixtureDef.density = DENSITY;
		fixtureDef.friction = 0f;
		fixtureDef.restitution = 0f; // Make it bounce a little bit

		// Create our fixture and attach it to the body
		final Fixture fixture = body.createFixture(fixtureDef);
		
		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		box.dispose();	
		return body;
	}

	private static float convertAngle( float angleInDeg ) {
		if ( angleInDeg > 180 ) {
			return angleInDeg - 360;
		}
		return angleInDeg;
	}

	private static float degToRad(float degree) {
        return (float) ( degree * (Math.PI/180d) );
	}
}
