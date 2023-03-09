/**
 * Copyright 2013-2014 Javier Placencio, 2023 Ignite Realtime Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cl.clayster.exi;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;

import java.io.FileWriter;
import java.io.IOException;


public class FilePrinterFilter extends IoFilterAdapter
{
    public static String filterName = "stats";

    public FilePrinterFilter()
    {
    }

    int r = 1;

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception
    {
        if (message instanceof String) {
            addMsg(EXIUtils.bytesToHex(((String) message).getBytes()), session.hashCode());
        } else if (message instanceof IoBuffer) {
            IoBuffer byteBuffer = (IoBuffer) message;
            // Keep current position in the buffer
            int currentPos = byteBuffer.position();

            byte[] msg = byteBuffer.array();
            addMsg(EXIUtils.bytesToHex(msg), session.hashCode());

            byteBuffer.position(currentPos);
        }
        super.messageReceived(nextFilter, session, message);
    }

    private void addMsg(String msg, int id) throws IOException
    {
        //Log.debug("writing for {}: {}", id, msg);
        FileWriter f = new FileWriter("output" + id + ".txt", true);
        f.write(msg + "\n");
        f.close();
    }
}
