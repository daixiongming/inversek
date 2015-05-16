package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;

public class WorldModel implements ITickListener , IMathSupport
{
	private final World world;
	private float accumulator = 0;

	private final float floorY = 0;

	private final List<Ball> balls = new ArrayList<>(); 

	protected static enum ItemType
	{
		GROUND( (short) 1),
		ROBOT_BASE((short) 2), 
		BONE( (short) 4),
		BALL( (short) 8);

		public final short bitMask;

		private ItemType(short bitMask) {
			this.bitMask = bitMask;
		}
	}

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
		world  = new World( new Vector2(0,-9.81f) , true );
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
		final Vector2 position = new Vector2(0, floorY-Constants.FLOOR_THICKNESS/2f);
		newStaticBody( ItemType.GROUND , position )
		.boxShape(100 , Constants.FLOOR_THICKNESS)
		.collidesWith(ItemType.BALL)
		.build();
	}

	public void addBall(float x,float y) 
	{
		System.out.println("Adding ball @ "+x+","+y);

		// Create our body in the world using our body definition
		final Body body = newDynamicBody(ItemType.BALL,new Vector2(x,y))
				.circleShape( Constants.BALL_RADIUS )
				.collidesWith( ItemType.GROUND  , ItemType.BONE , ItemType.BALL )
				.restitution( 0.3f )
				.build();

		balls.add( new Ball( body , Constants.BALL_RADIUS ) );
	}
	
	public void destroyBall(Ball ball) 
	{
		System.out.println("Destroyed ball "+ball);
		balls.remove( ball );
		world.destroyBody( ball.body );
	}

	@Override
	public boolean tick(float deltaSeconds) 
	{
		// fixed time step
		// max frame time to avoid spiral of death (on slow devices)
		float frameTime = Math.min(deltaSeconds, 0.25f);
		accumulator += frameTime;
		while (accumulator >= Constants.PHYSICS_TIMESTEP)
		{
			world.step(Constants.PHYSICS_TIMESTEP, Constants.VELOCITY_ITERATIONS , Constants.POSITION_ITERATIONS );
			accumulator -= Constants.PHYSICS_TIMESTEP;
		}
		return true;
	}

	private List<Bone> sortLeftToRight(List<Bone> bones) 
	{
		final List<Bone> copy = new ArrayList<>(bones);

		final List<Bone> roots = copy.stream().filter( b -> b.jointA.predecessor == null ).collect( Collectors.toList() );
		if ( roots.size() != 1 ) {
			throw new IllegalArgumentException("Expected 1 root bone, found "+roots.size());
		}

		Bone previous = roots.get(0);
		copy.remove( previous );

		final List<Bone> sorted = new ArrayList<>();		
		sorted.add( previous );
		outer:		
			while ( ! copy.isEmpty() ) 
			{
				for (Iterator<Bone> it = copy.iterator(); it.hasNext();) 
				{
					Bone current = it.next();
					if ( current.jointA == previous.jointB ) 
					{
						sorted.add( current );
						it.remove();
						previous = current;
						continue outer;
					}
					throw new RuntimeException("Found no successor for "+previous);
				}
			}
		if ( sorted.size() != bones.size() ) {
			throw new RuntimeException("Something went wrong");
		}
		return sorted;
	}

	public void add(RobotArm arm) 
	{
		// create robot base
		final Body robotBase = createRobotBase();
		arm.setBase( robotBase );

		// create bones first because the joints will
		// link the bones
		final KinematicsChain chain=arm.getModel().getChains().get(0);
		final List<Bone> sortedBones = sortLeftToRight( chain.getBones() );

		final float y = Constants.ROBOTBASE_HEIGHT;
		float x1 =  de.codesourcery.inversek.Constants.JOINT_RADIUS;
		for ( Bone b : sortedBones ) 
		{
			if ( b instanceof Gripper ) {
				createHorizontalGripper( new Vector2( x1 + b.length/2 ,y) , (Gripper) b );
			} else {
				createHorizontalBone( new Vector2( x1 + b.length/2 ,y) , b );
			}
			x1 += b.length + 2 * de.codesourcery.inversek.Constants.JOINT_RADIUS;
		}

		// create joints
		final Vector2 center = new Vector2(0 , Constants.ROBOTBASE_HEIGHT ); 		
		for ( Bone b : sortedBones ) 
		{
			final de.codesourcery.inversek.Joint joint = b.jointA;
			joint.setOrientation(0);
			joint.position.set( center.cpy() );
			System.out.println("Joint @ "+center);

			final Body left = joint.predecessor == null ? robotBase: joint.predecessor.getBody();
			final Body right = joint.successor.getBody();
			createJoint(joint,left,right);
			center.add( 2*de.codesourcery.inversek.Constants.JOINT_RADIUS+joint.successor.length , 0 );
		}

		// update positions
		chain.getRootJoint().successor.forwardKinematics();
	}	

	public void createJoint(de.codesourcery.inversek.Joint joint,Body predecessor,Body successor) 
	{
		final RevoluteJointDef def = new RevoluteJointDef();
		def.collideConnected=false;
		def.bodyA = predecessor;
		if ( joint.predecessor == null ) { // attached to base
			def.localAnchorA.set( 0 , Constants.ROBOTBASE_HEIGHT + Constants.JOINT_RADIUS);
		} else {
			def.localAnchorA.set( joint.predecessor.length/2+Constants.JOINT_RADIUS , 0 );
		}
		def.bodyB = successor;
		def.localAnchorB.set( -joint.successor.length/2 - Constants.JOINT_RADIUS , 0 );

		float lowerAngleLimitInDeg;
		float upperAngleLimitInDeg;
		if ( joint.range.getMinimumAngle() == 0 && joint.range.getMaximumAngle() == 360 ) {
			lowerAngleLimitInDeg = degToRad(0);
			upperAngleLimitInDeg = degToRad(360);  			
		} else {
			lowerAngleLimitInDeg = degToRad(0);  
			upperAngleLimitInDeg = degToRad(0);  
		}
		
		def.enableLimit = true;		
		def.lowerAngle = degToRad( -270 );
		def.upperAngle = degToRad( 45 );
		def.referenceAngle = 0;
		
		System.out.println("Created joint "+joint+" with limits "+
				lowerAngleLimitInDeg+"° ("+def.lowerAngle+" rad) -> "+
				upperAngleLimitInDeg+"° ("+def.upperAngle+" rad)");

		final RevoluteJoint j = (RevoluteJoint) world.createJoint(def);
		joint.setBody( j );
	}

	private Body createRobotBase() 
	{
		final Vector2 center = new Vector2(0,Constants.ROBOTBASE_HEIGHT/2);
		return newStaticBody( ItemType.BONE, center )
				.boxShape( Constants.ROBOTBASE_WIDTH , Constants.ROBOTBASE_HEIGHT ) 
				.collidesWith(ItemType.BALL)
				.build();
	}

	private Body createHorizontalBone(Vector2 center,Bone bone) 
	{
		final Body body = newDynamicBody( ItemType.BONE, center)
				.boxShape( bone.length, Constants.BONE_THICKNESS ) 
				.collidesWith( ItemType.BALL )
				.gravityScale(0)
				.build();
		bone.setBody( body );
		return body;
	}	
	
	private void createHorizontalGripper(Vector2 center,Gripper gripper) 
	{
		/* Gripper looks like this:
		 * 
		 *       BP222222222
		 *       B
		 * XXXXXXD
		 *       B
		 *       BP111111111
		 *       
		 * where 
		 * 
		 * X = bone the gripper is attached to
		 * B = Gripper base plate
		 * D = Distance joint with distance 0
		 * P = Prismatic joint
		 * 1 = lower part of claw
		 * 2 = upper part of claw
		 */
		
		// create bone the gripper is attached to
		final Body gripperBase = createHorizontalBone( center , gripper );		
		
		// create base plate
		final Vector2 basePlateCenter = new Vector2( center.x + gripper.length/2 + Constants.BASEPLATE_THICKNESS/2f , center.y );
		final BoxBuilder basePlateBuilder = newDynamicBody( ItemType.BONE , basePlateCenter );
		basePlateBuilder.boxShape( Constants.BASEPLATE_THICKNESS , gripper.getMaxBaseplateLength() );
		basePlateBuilder.gravityScale(0);
		
		final Body basePlate = basePlateBuilder.collidesWith(ItemType.BALL).build();
		
		// create lower part of claw
		final Vector2 lowerClawCenter = new Vector2( basePlateCenter.x + Constants.BASEPLATE_THICKNESS/2f + gripper.getClawLength()/2f,
				basePlateCenter.y - gripper.getMaxBaseplateLength()/2 + Constants.CLAW_THICKNESS/2f );
		
		final BoxBuilder lowerClawBuilder = newDynamicBody( ItemType.BONE , lowerClawCenter )
				.boxShape( gripper.getClawLength() , Constants.CLAW_THICKNESS )
				.gravityScale( 0 )
				.collidesWith( ItemType.BALL );
		final Body lowerClaw = lowerClawBuilder.build();
				
		// create upper part of claw
		final Vector2 upperClawCenter = new Vector2( basePlateCenter.x + Constants.BASEPLATE_THICKNESS/2f + gripper.getClawLength()/2f,
				basePlateCenter.y + gripper.getMaxBaseplateLength()/2 - Constants.CLAW_THICKNESS/2f );
		final BoxBuilder upperClawBuilder = newDynamicBody( ItemType.BONE , upperClawCenter )
				.boxShape( gripper.getClawLength() , Constants.CLAW_THICKNESS )
				.gravityScale( 0 )
				.collidesWith( ItemType.BALL );
		final Body upperClaw = upperClawBuilder.build();		
		
		// create distance joint connecting the base plate with the gripper bone
		final WeldJointDef distJointDef = new WeldJointDef();
		distJointDef.collideConnected=false;
		distJointDef.bodyA = gripperBase;
		distJointDef.localAnchorA.set( gripper.length/2, 0 );
		distJointDef.bodyB = basePlate; 
		distJointDef.localAnchorB.set( -Constants.BASEPLATE_THICKNESS/2f , 0 );
		// distJointDef.length = 0;
		world.createJoint( distJointDef );
		
		// create prismatic joint connecting base plate and lower part of claw
		final PrismaticJointDef lowerJointDef = new PrismaticJointDef();
		lowerJointDef.collideConnected=false;
		lowerJointDef.bodyA = basePlate;
		lowerJointDef.localAnchorA.set( Constants.BASEPLATE_THICKNESS/2 , -gripper.getMaxBaseplateLength()/2f - Constants.CLAW_THICKNESS/2f );
		lowerJointDef.bodyB = lowerClaw; 
		lowerJointDef.localAnchorB.set( -gripper.getClawLength()/2f , 0 ); 
		lowerJointDef.referenceAngle = 0;
		lowerJointDef.enableMotor=false;
		lowerJointDef.localAxisA.set(0,1); 
		lowerJointDef.enableLimit = true;
		lowerJointDef.lowerTranslation=0;
		lowerJointDef.upperTranslation=gripper.getMaxBaseplateLength()/2f - Constants.CLAW_THICKNESS;
		
		final PrismaticJoint lowerClawJoint = (PrismaticJoint) world.createJoint(lowerJointDef);
		
		// create prismatic joint connecting base plate and lower part of claw
		final PrismaticJointDef upperJointDef = new PrismaticJointDef();
		upperJointDef.collideConnected=false;
		upperJointDef.bodyA = basePlate;
		upperJointDef.localAnchorA.set( Constants.BASEPLATE_THICKNESS/2 , gripper.getMaxBaseplateLength()/2f - Constants.CLAW_THICKNESS/2f );
		upperJointDef.bodyB = upperClaw; 
		upperJointDef.localAnchorB.set( -gripper.getClawLength()/2f , 0  ); 
		upperJointDef.referenceAngle = 0;
		upperJointDef.enableMotor=false;
		upperJointDef.localAxisA.set(0,1); 
		upperJointDef.enableLimit = true;
		upperJointDef.lowerTranslation=0; 
		upperJointDef.upperTranslation= gripper.getMaxBaseplateLength()/2f - Constants.CLAW_THICKNESS;
		
		final PrismaticJoint upperClawJoint = (PrismaticJoint) world.createJoint(upperJointDef);
		
		gripper.setBasePlateBody( basePlate );
		gripper.setLowerClawBody( lowerClaw );
		gripper.setUpperClawBody( upperClaw );
		gripper.setLowerJoint( lowerClawJoint );
		gripper.setUpperJoint( upperClawJoint );
	}
	
	private static float convertAngle( float angleInDeg ) {
		if ( angleInDeg > 180 ) {
			return angleInDeg - 360;
		}
		return angleInDeg;
	}

	private BoxBuilder newDynamicBody(ItemType type,Vector2 center) 
	{
		return newBody(type,center,false);
	}

	private BoxBuilder newStaticBody(ItemType type,Vector2 center) 
	{	
		return newBody(type,center,true);
	}

	private BoxBuilder newBody(ItemType type,Vector2 center,boolean isStatic) 
	{
		return new BoxBuilder(type,center,isStatic);
	}

	protected final class BoxBuilder 
	{
		private final Vector2 center;
		private final ItemType itemType;
		private final Set<ItemType> collidesWith = new HashSet<>();
		
		private boolean isStatic;
		private Shape shape;
		private boolean isBuilt;
		private float restitution=0;
		private float friction;
		private float gravityScale=1f;

		public BoxBuilder(ItemType type,Vector2 center, boolean isStatic) 
		{
			this.itemType = type;
			this.center = center.cpy();
			this.isStatic = isStatic;
		}
		
		public BoxBuilder gravityScale(float gravityScale) {
			this.gravityScale = gravityScale;
			return this;
		}

		public BoxBuilder restitution(float value) {
			this.restitution = value;
			return this;
		}

		public BoxBuilder friction(float value) {
			this.friction = value;
			return this;
		}		

		public BoxBuilder collidesWith(ItemType t1,ItemType... other) 
		{
			assertNotBuilt();

			if ( t1 == null ) {
				throw new IllegalArgumentException("t1 must not be NULL");
			}
			collidesWith.add( t1 );
			if ( other != null ) {
				collidesWith.addAll( Arrays.asList( other ) );
			}
			return this;
		}

		private void assertNotBuilt() {
			if ( isBuilt ) {
				throw new IllegalStateException("Already built!");
			}
		}

		public BoxBuilder circleShape(float radius) {
			assertNotBuilt();
			final CircleShape circle = new CircleShape();
			circle.setRadius( radius );
			this.shape = circle;
			return this;
		}

		public BoxBuilder boxShape(float width, float height) {
			assertNotBuilt();

			if ( this.shape != null ) {
				throw new IllegalStateException("Shape already set to "+this.shape);
			}
			final PolygonShape box = new PolygonShape();
			box.setAsBox( width/2f , height/2f );
			this.shape = box;
			return this;
		}

		public Body build() 
		{
			assertNotBuilt();

			if ( shape == null ) {
				throw new IllegalStateException("Shape not set?");
			}

			System.out.println("Creating "+(isStatic?"static":"dynamic")+" body @ "+center+" with shape "+this.shape);

			final BodyDef bodyDef = new BodyDef();
			bodyDef.type = isStatic ? BodyType.StaticBody : BodyType.DynamicBody;
			bodyDef.position.set( center );
			bodyDef.gravityScale = this.gravityScale;

			final Body body = world.createBody(bodyDef);

			FixtureDef fixtureDef = new FixtureDef();
			fixtureDef.shape = this.shape;
			fixtureDef.density = Constants.DENSITY;
			fixtureDef.friction = this.friction;
			fixtureDef.restitution = this.restitution; 
			fixtureDef.filter.categoryBits = this.itemType.bitMask;
			fixtureDef.filter.maskBits = (short) collidesWith.stream().mapToInt( m -> m.bitMask ).sum();

			body.createFixture(fixtureDef);
			this.shape.dispose();
			this.shape = null;
			return body;			
		}
	}
}