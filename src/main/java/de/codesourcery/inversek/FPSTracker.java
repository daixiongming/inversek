package de.codesourcery.inversek;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class FPSTracker {

	private static final int RINGBUFFER_SIZE=150;

	private final BufferedImage image;
	private final Graphics2D graphics;

	private final float[] ringBuffer = new float[RINGBUFFER_SIZE];

	private float maxFps = 0;
	private long frameCount = 0;

	private int readPtr;
	private int writePtr;
	private int elementsInBuffer;

	private final Dimension size;

	public FPSTracker()
	{
		this.image = new BufferedImage(RINGBUFFER_SIZE,50,BufferedImage.TYPE_INT_RGB);
		this.graphics = image.createGraphics();
		this.size = new Dimension(image.getWidth(),image.getHeight());
	}

	public BufferedImage getImage() {
		return image;
	}

	public Dimension getSize() {
		return size;
	}

	public void renderFPS(float deltaSeconds)
	{
		final float fps = 1.0f/deltaSeconds;
		frameCount++;

		ringBuffer[ writePtr ] = fps;
		writePtr = (writePtr+1) % RINGBUFFER_SIZE;

		elementsInBuffer = Math.max( elementsInBuffer+1, RINGBUFFER_SIZE );
		if ( elementsInBuffer == RINGBUFFER_SIZE ) {
			readPtr = (readPtr+1) % RINGBUFFER_SIZE;
		}

		if ( frameCount > 20 ) { // avoid distorting measurements by skipping some frames until the JIT compiler has kicked in
			maxFps = Math.max( fps ,  maxFps );
		}

		final float range = maxFps;

		// clear render buffer
		graphics.setColor(Color.BLACK);
		graphics.fillRect(0,0,image.getWidth(),image.getHeight());

		// render chart
		final int yOffset = 20;
		final int imageHeight = image.getHeight()-  yOffset;
		final int y0 = image.getHeight();
		graphics.setColor(Color.GREEN);

		float bufferMinFps = 10000;
		float bufferMaxFps = 0;
		float bufferSumFps = 0;
		for ( int i = elementsInBuffer, x= 0 , ptr = readPtr ; i > 0 ; i--,x++)
		{
			final float value = ringBuffer[ ptr ];
			ptr = (ptr+1) % RINGBUFFER_SIZE;

			bufferSumFps += value;
			bufferMinFps = value < bufferMinFps ? value : bufferMinFps;
			bufferMaxFps = value > bufferMaxFps ? value : bufferMaxFps;

			final float percentage = value / range;
			final int y= y0 - (int) (imageHeight*percentage);
			graphics.drawLine(x,y0,x,y);
		}
		final float bufferAvgFps = bufferSumFps / elementsInBuffer;

		// draw FPS string
		graphics.setColor(Color.WHITE);
		graphics.drawString("FPS: "+(int) bufferMinFps+" / "+(int) bufferAvgFps+" / "+(int) bufferMaxFps,5,15);
	}
}
