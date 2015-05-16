package de.codesourcery.inversek;

public interface IMathSupport {

	public default float radToDeg(float rad) {
		return (float) (rad * 180d/Math.PI);
	}
	
	public default float degToRad(float degree) {
		return (float) (degree * Math.PI/180d);
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
