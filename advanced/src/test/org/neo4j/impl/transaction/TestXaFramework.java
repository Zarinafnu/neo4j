/*
 * Copyright 2002-2007 Network Engine for Objects in Lund AB [neotechnology.com]
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.impl.transaction;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.neo4j.api.core.Node;
import org.neo4j.impl.AbstractNeoTestCase;
import org.neo4j.impl.transaction.xaframework.LogBuffer;
import org.neo4j.impl.transaction.xaframework.XaCommand;
import org.neo4j.impl.transaction.xaframework.XaCommandFactory;
import org.neo4j.impl.transaction.xaframework.XaConnection;
import org.neo4j.impl.transaction.xaframework.XaConnectionHelpImpl;
import org.neo4j.impl.transaction.xaframework.XaContainer;
import org.neo4j.impl.transaction.xaframework.XaDataSource;
import org.neo4j.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.impl.transaction.xaframework.XaResourceHelpImpl;
import org.neo4j.impl.transaction.xaframework.XaResourceManager;
import org.neo4j.impl.transaction.xaframework.XaTransaction;
import org.neo4j.impl.transaction.xaframework.XaTransactionFactory;

public class TestXaFramework extends AbstractNeoTestCase
{	
	private TransactionManager tm; 
	private XaDataSourceManager xaDsMgr; 

	public TestXaFramework( String name )
	{
		super( name );
	}
	
	public static Test suite()
	{
		return new TestSuite( TestXaFramework.class );
	}
	
	public void setUp()
	{
		super.setUp();
		getTransaction().finish();
		TxModule txModule = getEmbeddedNeo().getConfig().getTxModule();
		tm = txModule.getTxManager();
		xaDsMgr = txModule.getXaDataSourceManager();
	}
	
	private static class DummyCommand extends XaCommand
	{
		private int type = -1;
		
		DummyCommand( int type )
		{
			this.type = type;
		}
		
		public void execute() {}
//		public void writeToFile( FileChannel fileChannel, ByteBuffer buffer ) 
//			throws IOException 
		public void writeToFile( LogBuffer buffer ) throws IOException 
		{
//			buffer.clear();
			buffer.putInt( type );
//			buffer.flip();
//			fileChannel.write( buffer );
		} 
	}
	
	private static class DummyCommandFactory extends XaCommandFactory
	{
		public XaCommand readCommand( FileChannel fileChannel, 
			ByteBuffer buffer ) throws IOException
		{
			buffer.clear();
			buffer.limit( 4 );
			if ( fileChannel.read( buffer ) == 4 )
			{
				buffer.flip();
				return new DummyCommand( buffer.getInt() );
			}
			return null;
		}
	}
	
	private static class DummyTransaction extends XaTransaction
	{
		private java.util.List<XaCommand> commandList = 
			new java.util.ArrayList<XaCommand>();
		
		public DummyTransaction( int identifier, XaLogicalLog log )
		{
			super( identifier, log );
		}

		public void doAddCommand( XaCommand command ) 
		{
			commandList.add( command );
		}
		
		public XaCommand[] getCommands()
		{
			return commandList.toArray( new XaCommand[ commandList.size() ] );
		}
		
		public void doPrepare()
		{
			
		}
		
		public void doRollback() {}
		public void doCommit() {}		
		public boolean isReadOnly() { return false; }
	}
	
	private static class DummyTransactionFactory extends XaTransactionFactory
	{
		public XaTransaction create( int identifier )
		{
			return new DummyTransaction( identifier, getLogicalLog() );
		}

		@Override
        public void lazyDoneWrite( List<Integer> identifiers ) throws XAException
        {
        }
	}
	
	public class DummyXaDataSource extends XaDataSource
	{
		private XaContainer xaContainer = null;
		
		public DummyXaDataSource( java.util.Map<?,?> map ) 
			throws InstantiationException
		{
			super( map );
			try
			{
				xaContainer = XaContainer.create( "dummy_resource", 
					new DummyCommandFactory(), new DummyTransactionFactory() );
				xaContainer.openLogicalLog();
			}
			catch ( IOException e )
			{
				throw new InstantiationException( "" + e );
			}
		}
		
		public void close()
		{
			xaContainer.close();
			// cleanup dummy resource log
			File dir = new File( "." );
			File files[] = dir.listFiles( new FilenameFilter() 
				{ 
					public boolean accept( File dir, String fileName )
					{
						return fileName.startsWith( "dummy_resource" );
					}
				} );
			for ( int i = 0; i < files.length; i++ )
			{
				files[i].delete();
			}
		}
		
		public XaConnection getXaConnection()
		{
			return new DummyXaConnection( xaContainer.getResourceManager() );
		}

        @Override
        public byte[] getBranchId()
        {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setBranchId( byte[] branchId )
        {
            // TODO Auto-generated method stub
            
        }
	}
	
	private static class DummyXaResource extends XaResourceHelpImpl
	{
		DummyXaResource( XaResourceManager xaRm ) 
		{
			super( xaRm, null );
		}
		
		public boolean isSameRM( XAResource resource )
		{
			if ( resource instanceof DummyXaResource )
			{
				return true;
			}
			return false;
		}
	}
	
	private class DummyXaConnection extends XaConnectionHelpImpl
	{
		private XAResource xaResource = null;
		
		public DummyXaConnection( XaResourceManager xaRm )
		{
			super( xaRm );
			xaResource = new DummyXaResource( xaRm );
		}
		
		public XAResource getXaResource()
		{
			return xaResource;
		}
		
		public void doStuff1() throws XAException
		{
			validate();
			getTransaction().addCommand( new DummyCommand(1) );
		}
		
		public void doStuff2() throws XAException
		{
			validate();
			getTransaction().addCommand( new DummyCommand(2) );
		}
		
		public void enlistWithTx() throws Exception
		{
			tm.getTransaction().enlistResource( xaResource );
		}
		
		public void delistFromTx() throws Exception
		{
			tm.getTransaction().delistResource( xaResource, 
				XAResource.TMSUCCESS );
		}
		
		public int getTransactionId() throws Exception
		{
			return getTransaction().getIdentifier();
		}
	}
	
	public void testCreateXaResource()
	{
		try
		{
			xaDsMgr.registerDataSource( "dummy_datasource", 
				new DummyXaDataSource( new java.util.HashMap<Object,Object>() ), 
				"DDDDDD".getBytes() );
		}
		catch ( Exception e )
		{
			fail( "" + e );
		}
		XaDataSource xaDs = xaDsMgr.getXaDataSource( 
			"dummy_datasource" );
		DummyXaConnection xaC = null;
		try
		{
			xaC = ( DummyXaConnection ) xaDs.getXaConnection();
			try
			{
				xaC.doStuff1();
				fail( "Non enlisted resource should throw exception" );
			}
			catch ( XAException e )
			{ // good
			}
			Xid xid = new XidImpl();
			xaC.getXaResource().start( xid, XAResource.TMNOFLAGS );
			try
			{
				xaC.doStuff1();
				xaC.doStuff2();
			}
			catch ( XAException e )
			{
				fail( "Enlisted resource should not throw exception" );			
			}
			xaC.getXaResource().end( xid, XAResource.TMSUCCESS );
			xaC.getXaResource().prepare( xid );
			xaC.getXaResource().commit( xid, false );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			fail( "" + e );
		}
		finally
		{
			xaDsMgr.unregisterDataSource( "dummy_datasource" );
			if ( xaC != null )
			{
				xaC.destroy();
			}
		}
		// cleanup dummy resource log
		File dir = new File( "." );
		File files[] = dir.listFiles( new FilenameFilter() 
			{ 
				public boolean accept( File dir, String fileName )
				{
					return fileName.startsWith( "dummy_resource" );
				}
			} );
		for ( int i = 0; i < files.length; i++ )
		{
			files[i].delete();
		}		
	}
	
	public void testTxIdGeneration()
	{
		DummyXaDataSource xaDs1 = null;
		DummyXaConnection xaC1 = null;
		try
		{
			xaDsMgr.registerDataSource( "dummy_datasource1", 
				new DummyXaDataSource( new java.util.HashMap<Object,Object>() ), 
				"DDDDDD".getBytes() );
			xaDs1 = ( DummyXaDataSource ) xaDsMgr.getXaDataSource( 
				"dummy_datasource1" );
			xaC1 = ( DummyXaConnection ) xaDs1.getXaConnection();
			tm.begin(); // get
			xaC1.enlistWithTx();
			int currentTxId = xaC1.getTransactionId();
			xaC1.doStuff1();
			xaC1.delistFromTx();
			tm.commit();
			// xaC2 = ( DummyXaConnection ) xaDs2.getXaConnection();
			tm.begin();
			Node node = getNeo().createNode(); // get neo resource in tx
			xaC1.enlistWithTx();
			assertEquals( ++currentTxId, xaC1.getTransactionId() );
			xaC1.doStuff1();
			xaC1.delistFromTx();
			tm.commit();
			tm.begin();
			node = getNeo().getNodeById( (int) node.getId() );
			xaC1.enlistWithTx();
			assertEquals( ++currentTxId, xaC1.getTransactionId() );
			xaC1.doStuff2();
			xaC1.delistFromTx();
			node.delete();
			tm.commit();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			fail( "" + e );
		}
		finally
		{
			xaDsMgr.unregisterDataSource( "dummy_datasource1" );
			// xaDsMgr.unregisterDataSource( "dummy_datasource1" );
			if ( xaC1 != null )
			{
				xaC1.destroy();
			}
		}
		// cleanup dummy resource log
		File dir = new File( "." );
		File files[] = dir.listFiles( new FilenameFilter() 
			{ 
				public boolean accept( File dir, String fileName )
				{
					return fileName.startsWith( "dummy_resource" );
				}
			} );
		for ( int i = 0; i < files.length; i++ )
		{
			files[i].delete();
		}		
	}
	

	private static class XidImpl implements Xid
	{
		private static final int FORMAT_ID = 0xDDDDDDDD;

		private static final byte INSTANCE_ID[] = new byte[]  
			{ 'N', 'E', 'O', 'K', 'E', 'R', 'N', 'L' };
		private static final byte DUMMY_BRANCH_ID[] = new byte[]
			{ 'D', 'U', 'M', 'M', 'Y' };
		private static long seqId = 0;
	
		private byte globalId[] = null; 
		private byte branchId[] = null;

		XidImpl()
		{
			globalId = getNewGlobalId();
			branchId = DUMMY_BRANCH_ID;
		}
		
		private static long getNextSequenceId()
		{
			return seqId++;
		}

		static byte[] getNewGlobalId()
		{
			// create new global id ( [INSTANCE_ID][time][sequence] )
			byte globalId[] = new byte[ INSTANCE_ID.length + 16 ];
			System.arraycopy( INSTANCE_ID, 0, globalId, 0, INSTANCE_ID.length );
			long time = System.currentTimeMillis();
			long sequence = getNextSequenceId();
			for ( int i = 0; i < 8; i++ )
			{
				globalId[ INSTANCE_ID.length + i ] = 
					( byte ) ( ( time >> ( ( 7 - i ) * 8 ) ) & 0xFF );
			}
			for ( int i = 0; i < 8; i++ )
			{
				globalId[ INSTANCE_ID.length + 8 + i ] = 
					( byte ) ( ( sequence >> ( ( 7 - i ) * 8 ) ) & 0xFF );
			}
			return globalId;
		}
		
		public byte[] getGlobalTransactionId()
		{
			return globalId.clone();
		}
	
		public byte[] getBranchQualifier()
		{
			return branchId.clone();
		}
		
		public int getFormatId()
		{
			return FORMAT_ID;
		}
		
		public boolean equals( Object o ) 
		{
			if ( !( o instanceof Xid ) )
			{
				return false;
			}
			byte otherGlobalId[] = ( ( Xid ) o ).getGlobalTransactionId();
			byte otherBranchId[] = ( ( Xid ) o ).getBranchQualifier();
	
			if ( globalId.length != otherGlobalId.length || 
				branchId.length != otherBranchId.length )
			{
				return false;
			}
			
			for ( int i = 0; i < globalId.length; i++ )
			{
				if ( globalId[i] != otherGlobalId[i] )
				{
					return false;
				}
			}
			for ( int i = 0; i < branchId.length; i++ )
			{
				if ( branchId[i] != otherBranchId[i] )
				{
					return false;
				}
			}
			return true;
		}
		
		private volatile int hashCode = 0;
	
		public int hashCode()
		{
			if ( hashCode == 0 )
			{
				int calcHash = 0;
				for ( int i = 0; i < 4 && i < globalId.length; i++ )
				{
					calcHash += globalId[ globalId.length - i - 1 ] << i * 8;
				}
				hashCode = 3217 * calcHash;
			}
			return hashCode;
		}
	}
}
