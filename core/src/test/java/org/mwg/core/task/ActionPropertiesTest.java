package org.mwg.core.task;


import org.junit.Assert;
import org.junit.Test;
import org.mwg.*;
import org.mwg.task.*;

import static org.mwg.task.Actions.propertiesWithTypes;

public class ActionPropertiesTest {
    private Graph graph;

    public void initGraph() {
        graph = new GraphBuilder().build();
        graph.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean result) {
                Node root = graph.newNode(0,0);
                root.setProperty("id", Type.INT,1);
                root.setProperty("attribute",Type.BOOL,false);
                graph.index("root",root,"id",null);

                Node child1 = graph.newNode(0,0);
                child1.set("name","child1");
                root.add("rel1",child1);

                root.index("localIindex1",child1,"name",null);
            }
        });
    }

    public void deleteGraph() {
        graph.disconnect(null);
    }

    @Test
    public void testNormalRelations() {
        initGraph();
        Actions.fromIndexAll("root")
                .properties()
                .then(new Action() {
                    @Override
                    public void eval(TaskContext context) {
                        TaskResult<String> result = context.result();
                        Assert.assertEquals(4,result.size());

                        Assert.assertEquals("id",result.get(0));
                        Assert.assertEquals("attribute",result.get(1));
                        Assert.assertEquals("rel1",result.get(2));
                        Assert.assertEquals("localIindex1",result.get(3));
                        context.continueTask();
                    }
                })
                .execute(graph,null);
        deleteGraph();
    }

    @Test
    public void testLocalIndex() {
        initGraph();
        Actions.fromIndexAll("root")
                .subTasks(new Task[]{propertiesWithTypes(Type.RELATION),propertiesWithTypes(Type.LONG_TO_LONG_ARRAY_MAP)})
                .then(new Action() {
                    @Override
                    public void eval(TaskContext context) {
                        TaskResult<String> result = context.result();
                        Assert.assertEquals(2,result.size());

                        Assert.assertEquals("rel1",result.get(0));
                        Assert.assertEquals("localIindex1",result.get(1));
                    }
                })
                .execute(graph,null);
        deleteGraph();
    }
}