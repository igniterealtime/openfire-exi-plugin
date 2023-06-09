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

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.FidelityOptions;

public class SetupValues
{
    final static String ALIGNMENT = "alignment";
    final static String COMPRESSION = "compression";
    final static String STRICT = "strict";
    final static String PRESERVE_COMMENTS = "preserveComments";
    final static String PRESERVE_PIS = "preservePIs";
    final static String PRESERVE_DTD = "preserveDTD";
    final static String PRESERVE_PREFIXES = "preservePrefixes";
    final static String PRESERVE_LEXICAL = "preserveLexical";
    final static String SELF_CONTAINED = "selfContained";
    final static String BLOCK_SIZE = "blockSize";
    final static String VALUE_MAX_LENGTH = "valueMaxLength";
    final static String VALUE_PARTITION_CAPACITY = "valuePartitionCapacity";
    final static String WIDE_BUFFERS = "sessionWideBuffers";

    static CodingMode getCodingMode(String alignment)
    {
        CodingMode cm = CodingMode.BIT_PACKED;
        switch (alignment) {
            case "byte-packed":
                cm = CodingMode.BYTE_PACKED;
                break;
            case "pre-compression":
                cm = CodingMode.PRE_COMPRESSION;
                break;
            case "compression":
                cm = CodingMode.COMPRESSION;
                break;
        }
        return cm;
    }

    static String getFidelityOptionString(String fidelity)
    {
        String preserve = "";
        switch (fidelity) {
            case FidelityOptions.FEATURE_COMMENT:
                preserve = "preserveComments";
                break;
            case FidelityOptions.FEATURE_DTD:
                preserve = "preserveDTD";
                break;
            case FidelityOptions.FEATURE_LEXICAL_VALUE:
                preserve = "preserveLexical";
                break;
            case FidelityOptions.FEATURE_PI:
                preserve = "preservePIs";
                break;
            case FidelityOptions.FEATURE_PREFIX:
                preserve = "preservePrefixes";
                break;
            case FidelityOptions.FEATURE_SC:
                preserve = "selfContained";
                break;
            case FidelityOptions.FEATURE_STRICT:
                preserve = "strict";
                break;
        }
        return preserve;
    }
}
