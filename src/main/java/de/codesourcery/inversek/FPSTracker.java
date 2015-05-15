package de.codesourcery.inversek;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class FPSTracker {

	private static final int RINGBUFFER_SIZE=100;
	
	private final BufferedImage image;
	private final Graphics2D graphics;
	
	private final float[] ringBuffer = new float[RINGBUFFER_SIZE];
	
	private int readPtr;
	private int writePtr;
	private int elementsInBuffer;
	
	public FPSTracker() 
	{
		this.image = new BufferedImage(RINGBUFFER_SIZE,50,BufferedImage.TYPE_INT_RGB);
		this.graphics = image.createGraphics();
	}
	
	public BufferedImage getImage() {
		return image;
	}
	
	public void renderFPS(float deltaSeconds) 
	{
		final float fps = 1.0f/deltaSeconds;
		ringBuffer[ writePtr ] = fps;
		elementsInBuffer = Math.max( elementsInBuffer+1, RINGBUFFER_SIZE );
		
		writePtr = (writePtr+1) % RINGBUFFER_SIZE;
		if ( elementsInBuffer == RINGBUFFER_SIZE ) {
			readPtr = (readPtr+1) % RINGBUFFER_SIZE;
		}
		
		float minFps = 10000;
		float maxFps = 0;
		float sumFps = 0;
		for ( int i = elementsInBuffer, ptr = readPtr ; i > 0 ; i-- ) 
		{
			float value = ringBuffer[ ptr % RINGBUFFER_SIZE ];
			sumFps += value;
			minFps = Math.min( value , minFps );
			maxFps = Math.max( value ,  maxFps );
		}
		float avgFps = sumFps / elementsInBuffer;
		float range = maxFps-minFps;

		// clear screen
		graphics.setColor(Color.BLACK);
		graphics.fillRect(0,0,image.getWidth(),image.getHeight());
		
		// render chart
		final int imageHeight = image.getHeight();
		graphics.setColor(Color.GREEN);
		for ( int i = elementsInBuffer, x= 0 , ptr = readPtr ; i > 0 ; i-- ) 
		{
			float value = ringBuffer[ ptr % RINGBUFFER_SIZE ] - minFps;
			float percentage = value / range;
			final int y= imageHeight - (int) (image.getHeight()*percentage);
			graphics.drawLine(x,imageHeight,x,y);
		}
		
		// render text
		graphics.setColor(Color.WHITE);
		graphics.drawString("FPS: "+(int) minFps+" / "+(int) avgFps+" / "+(int) maxFps,5,15);
	}
}
