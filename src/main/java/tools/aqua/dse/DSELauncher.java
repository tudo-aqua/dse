/*
 * Copyright (C) 2021, Automated Quality Assurance Group,
 * TU Dortmund University, Germany. All rights reserved.
 *
 * DSE (dynamic symbolic execution) is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tools.aqua.dse;

import org.apache.commons.cli.*;

public class DSELauncher {

    public static void main(String[] args) {
        DSEArguments arguments = new DSEArguments();
        Config config = null;
        try {
            CommandLine cli = arguments.parse(args);
            config = Config.fromCommandLine(cli);
        }
        catch (Throwable t) {
            arguments.usage(t.getMessage());
            return;
        }

        DSE dse = new DSE(config);
        dse.executeAnalysis();
    }

}
