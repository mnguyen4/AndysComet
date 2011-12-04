package org.cometd.client.transport;

/**
 * @version $Revision$ $Date: 2010-01-20 00:02:44 -0700 (Wed, 20 Jan 2010) $
 */
public class TransportException extends RuntimeException
{
    public TransportException()
    {
    }

    public TransportException(String message)
    {
        super(message);
    }

    public TransportException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public TransportException(Throwable cause)
    {
        super(cause);
    }
}
