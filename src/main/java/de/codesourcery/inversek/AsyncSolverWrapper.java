package de.codesourcery.inversek;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncSolverWrapper implements ISolver
{
	private static final ThreadPoolExecutor POOL;
	
	static {
		final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(100);
		
		final ThreadFactory threadFactory = new ThreadFactory() {
			
			private final AtomicLong ID = new AtomicLong(0);
			@Override
			public Thread newThread(Runnable r) 
			{
				final Thread t = new Thread(r);
				t.setDaemon(true);
				t.setName("solver-thread-"+ID.incrementAndGet());
				return t;
			}
		};
		POOL = new ThreadPoolExecutor( 1 , 1 , 300 , TimeUnit.SECONDS , queue , threadFactory , new AbortPolicy() );
	}
	
	private volatile Outcome result;
	private final ISolver solver;
	
	public AsyncSolverWrapper(final ISolver solver) 
	{
		this.solver = solver;
	}
	
	@Override
	public Outcome solve(int maxIterations) 
	{
		final Outcome tmp = result;
		if ( tmp == null ) 
		{
			submitTask();
			result = Outcome.PROCESSING;
			return Outcome.PROCESSING;
		}
		return tmp;
	}
	
	private void submitTask() 
	{
		POOL.submit( new Runnable() {

			@Override
			public void run() 
			{
				Outcome tmpResult = Outcome.FAILURE;
				boolean success = false;
				try {
					do {
						tmpResult = solver.solve( 20000 );
					} while ( ! solver.hasFinished() );
					success = true;
				} 
				catch(Exception e) {
					System.err.println("Solver threw exception !");
					e.printStackTrace();
				}
				finally 
				{
					if ( ! success ) 
					{
						result = Outcome.FAILURE;
					} else {
						result = tmpResult;
					}
				}
			}
		});		
	}

	@Override
	public KinematicsChain getChain() {
		return solver.getChain();
	}

	@Override
	public boolean hasFinished() {
		return result != Outcome.PROCESSING;
	}
}