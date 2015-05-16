package de.codesourcery.inversek;

public class Constants {

	public static final float FACTOR = 1;
	
	public static final float FLOOR_THICKNESS = FACTOR*1f;
	
	// Careful, all values are in meters
	public static final float BALL_RADIUS = FACTOR*0.05f; // 10 cm
	
	// bones
	public static final float BONE_THICKNESS = FACTOR*0.01f; // 1 cm
	public static final float BONE_BASE_LENGTH = FACTOR*0.2f; // 20 cm
	
	// gripper
	public static final float BASEPLATE_THICKNESS = FACTOR*0.01f; // 1 cm
	public static final float CLAW_THICKNESS = FACTOR*0.01f; // 1 cm
	public static final float CLAW_LENGTH = FACTOR*0.1f; // 10 cm 
	
	public static final float BASEPLASE_LENGTH = BONE_BASE_LENGTH/2;
	
	public static final float GRIPPER_BONE_LENGTH = BONE_BASE_LENGTH/2;
	
	// joints
	public static final float JOINT_RADIUS = FACTOR*0.01f; // 1 cm

	// robot base
	public static final float ROBOTBASE_WIDTH  = FACTOR*0.1f; // 10 cm
	public static final float ROBOTBASE_HEIGHT = FACTOR*0.2f; // 20 cm

}
