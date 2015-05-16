package de.codesourcery.inversek;

public interface IMathSupport {

	public default float radToDeg(float rad) {
		return (float) (rad * 180d/Math.PI);
	}
	
	public default float degToRad(float degree) {
		return (float) (degree * Math.PI/180d);
	}
	
	public default float box2dAngleToDeg(float box2dRad) {
		
		/*
		 * Box2d uses
		 *       |270
		 *       |
		 *   --------- 0
		 *  180  |
		 *       | 90
		 *       
		 *  while I use
		 *    
		 *       |90
		 * 180   |
		 *   --------- 0
		 *       |
		 *    270|     
		 */
		float angle = radToDeg( box2dRad );
		if ( angle < 0 ) {
			return 360+angle;
		}
		return angle;
	}
	
	public default float myAngleInDegToBox2d(float degrees) {
		/*
		 * I use
		 *    
		 *       |90
		 * 180   |
		 *   --------- 0
		 *       |
		 *    270|  
		 *    
		 * while Box2d uses
		 *       |270
		 *       |
		 *   --------- 0
		 *  180  |
		 *       | 90
		 */
		float angle = normalizeAngleInDeg(degrees );
		return degToRad( angle ); 
	}	
	
	
	public default float normalizeAngleInDeg(float value) 
	{
		while ( value > 360 ) {
			value -= 360;
		}
		while ( value < 0 ) {
			value += 360;
		}
		return value;
	}	
}
