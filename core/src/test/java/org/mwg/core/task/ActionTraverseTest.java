package org.mwg.core.task;

import org.junit.Assert;
import org.junit.Test;
import org.mwg.Callback;
import org.mwg.Node;
import org.mwg.Type;
import org.mwg.struct.RelationIndexed;
import org.mwg.task.ActionFunction;
import org.mwg.task.TaskContext;

import static org.mwg.core.task.Actions.*;
import static org.mwg.core.task.Actions.newTask;

public class ActionTraverseTest extends AbstractActionTest {

    @Test
    public void test() {
        initGraph();
        newTask().then(readGlobalIndex("nodes"))
                .then(traverse("children"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(ctx.resultAsNodes().get(0).get("name"), "n0");
                        Assert.assertEquals(ctx.resultAsNodes().get(1).get("name"), "n1");
                    }
                })
                .execute(graph, null);
        removeGraph();
    }

    @Test
    public void testParse() {
        initGraph();
        newTask().parse("readGlobalIndex(nodes).traverse(children)")
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(ctx.resultAsNodes().get(0).get("name"), "n0");
                        Assert.assertEquals(ctx.resultAsNodes().get(1).get("name"), "n1");
                    }
                })
                .execute(graph, null);
        removeGraph();
    }

    @Test
    public void testTraverseIndex() {
        initGraph();
        final Node node1 = graph.newNode(0, 0);
        node1.set("name", Type.STRING, "node1");
        node1.set("value", Type.INT, 1);

        final Node node2 = graph.newNode(0, 0);
        node2.set("name", Type.STRING, "node2");
        node2.set("value", Type.INT, 2);

        final Node node3 = graph.newNode(0, 12);
        node3.set("name", Type.STRING, "node3");
        node3.set("value", Type.INT, 3);

        final Node root = graph.newNode(0, 0);
        root.set("name", Type.STRING, "root2");

        graph.index(0, 0, "roots", rootIndex -> {
            rootIndex.addToIndex(root, "name");

            RelationIndexed irel = (RelationIndexed) root.getOrCreate("childrenIndexed", Type.RELATION_INDEXED);
            irel.add(node1, "name");
            irel.add(node2, "name");
            //  irel.add(node3, "name");

            root.travelInTime(12, new Callback<Node>() {
                @Override
                public void on(Node root12) {
                    RelationIndexed irel12 = (RelationIndexed) root12.getOrCreate("childrenIndexed", Type.RELATION_INDEXED);
                    irel12.add(node3, "name");
                }
            });

        });

        newTask()
                .then(travelInTime("0"))
                .then(readGlobalIndex("roots", "name", "root2"))
                .then(traverse("childrenIndexed", "name", "node2"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(1, ctx.result().size());
                        Assert.assertEquals("node2", ctx.resultAsNodes().get(0).get("name"));
                    }
                }).execute(graph, null);

        newTask().then(readGlobalIndex("rootIndex", "name", "root2"))
                .then(traverse("childrenIndexed", "name", "node3"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(0, ctx.result().size());
                    }
                }).execute(graph, null);

        newTask()
                .then(travelInTime("12"))
                .then(readGlobalIndex("roots", "name", "root2"))
                .then(traverse("childrenIndexed", "name", "node2"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(1, ctx.result().size());
                        Assert.assertEquals("node2", ctx.resultAsNodes().get(0).get("name"));
                    }
                }).execute(graph, null);

        newTask().then(travelInTime("0"))
                .then(readGlobalIndex("roots", "name", "root2"))
                .then(traverse("childrenIndexed"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(2, ctx.result().size());
                        Assert.assertEquals("node1", ctx.resultAsNodes().get(0).get("name"));
                        Assert.assertEquals("node2", ctx.resultAsNodes().get(1).get("name"));
                    }
                }).execute(graph, null);

        newTask()
                .then(travelInTime("13"))
                .then(readGlobalIndex("roots", "name", "root2"))
                .then(traverse("childrenIndexed"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(3, ctx.result().size());
                        Assert.assertEquals("node1", ctx.resultAsNodes().get(0).get("name"));
                        Assert.assertEquals("node2", ctx.resultAsNodes().get(1).get("name"));
                        Assert.assertEquals("node3", ctx.resultAsNodes().get(2).get("name"));
                    }
                }).execute(graph, null);
        removeGraph();
    }

    @Test
    public void indexedRelationTest() {
        initGraph();
        newTask()
                .then(createNode())
                .then(setAttribute("name", Type.STRING, "toto"))
                .then(setAsVar("child"))
                .then(createNode())
                .then(setAttribute("name", Type.STRING, "parent"))
                .then(setAsVar("parent"))
                .then(addVarToRelation("children", "child", "name"))
                .then(inject("toto"))
                .then(setAsVar("child_name"))
                .then(readVar("parent"))
                .then(traverse("children", "name", "{{child_name}}"))
                .then(println("{{result}}"))
                .then(context -> {
                    Assert.assertEquals(1, context.result().size());
                })
                .execute(graph, null);
    }
}
