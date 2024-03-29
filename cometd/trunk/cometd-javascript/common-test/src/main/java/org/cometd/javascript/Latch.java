package org.cometd.javascript;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.mozilla.javascript.ScriptableObject;

/**
 * @version $Revision: 1223 $ $Date: 2010-05-26 09:15:58 -0700 (Wed, 26 May 2010) $
 */
public class Latch extends ScriptableObject
{
    private volatile CountDownLatch latch;

    public String getClassName()
    {
        return "Latch";
    }

    public void jsConstructor(int count)
    {
        reset(count);
    }

    public void reset(int count)
    {
        latch = new CountDownLatch(count);
    }

    public boolean await(long timeout) throws InterruptedException
    {
        return latch.await(timeout, TimeUnit.MILLISECONDS);
    }

    public void jsFunction_countDown()
    {
        latch.countDown();
    }

    public long jsGet_count()
    {
        return latch.getCount();
    }
}
