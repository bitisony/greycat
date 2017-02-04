/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.importer;

import greycat.plugin.Plugin;
import greycat.task.Action;
import greycat.Graph;
import greycat.Type;
import greycat.plugin.ActionFactory;

public class ImporterPlugin implements Plugin {

    @Override
    public void start(Graph graph) {
        graph.actionRegistry().declaration(ImporterActions.READFILES).setParams(Type.STRING).setFactory(new ActionFactory() {
            @Override
            public Action create(Object[] params) {
                return new ActionReadFiles((String) params[0]);
            }
        });
        graph.actionRegistry().declaration(ImporterActions.READLINES).setParams(Type.STRING).setFactory(new ActionFactory() {
            @Override
            public Action create(Object[] params) {
                return new ActionReadLines((String) params[0]);
            }
        });
    }

    @Override
    public void stop() {

    }


}