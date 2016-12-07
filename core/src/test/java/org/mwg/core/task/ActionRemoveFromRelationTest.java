package org.mwg.core.task;

import org.junit.Assert;
import org.junit.Test;
import org.mwg.Callback;
import org.mwg.Node;
import org.mwg.struct.Relation;
import org.mwg.task.ActionFunction;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

import static org.mwg.core.task.Actions.*;
import static org.mwg.core.task.Actions.newTask;

public class ActionRemoveFromRelationTest extends AbstractActionTest {

    public ActionRemoveFromRelationTest() {
        super();
        initGraph();
    }

    @Test
    public void testWithOneNode() {
        Node relatedNode = graph.newNode(0, 0);

        final long[] id = new long[1];
        newTask().then(createNode())
                .then(inject(relatedNode))
                .then(defineAsGlobalVar("x"))
                .then(addVarToRelation("friend", "x"))
                .then(removeVarFromRelation("friend", "x"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertNotNull(ctx.result());
                        Node node = ctx.resultAsNodes().get(0);
                        Assert.assertEquals(((Relation) node.get("friend")).size(), 0);
                        id[0] = node.id();
                    }
                }).execute(graph, null);


        graph.lookup(0, 0, id[0], new Callback<Node>() {
            @Override
            public void on(Node result) {
                Assert.assertEquals(((Relation) result.get("friend")).size(), 0);
                result.free();
            }
        });
    }

    @Test
    public void testWithArray() {
        Node relatedNode = graph.newNode(0, 0);

        final long[] ids = new long[5];
        newTask()
                .then(inject(relatedNode))
                .then(defineAsGlobalVar("x"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Node[] nodes = new Node[5];
                        for (int i = 0; i < 5; i++) {
                            nodes[i] = graph.newNode(0, 0);
                        }
                        ctx.continueWith(ctx.wrap(nodes));
                    }
                })
                .then(addVarToRelation("friend", "x"))
                .then(removeVarFromRelation("friend", "x"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertNotNull(ctx.result());
                        TaskResult<Node> nodes = ctx.resultAsNodes();
                        for (int i = 0; i < 5; i++) {
                            Assert.assertEquals(((Relation) nodes.get(i).get("friend")).size(), 0);
                            ids[i] = nodes.get(i).id();
                        }
                    }
                }).execute(graph, null);

        for (int i = 0; i < ids.length; i++) {
            graph.lookup(0, 0, ids[i], new Callback<Node>() {
                @Override
                public void on(Node result) {
                    Assert.assertEquals(((Relation) result.get("friend")).size(), 0);
                }
            });
        }
    }

    @Test
    public void testWithNull() {
        Node relatedNode = graph.newNode(0, 0);

        final boolean[] nextCalled = new boolean[1];
        newTask()
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        ctx.continueWith(null);
                    }
                })
                .then(inject(relatedNode))
                .then(defineAsGlobalVar("x"))
                .then(addVarToRelation("friend", "x"))
                .then(removeVarFromRelation("friend", "x"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        nextCalled[0] = true;
                    }
                }).execute(graph, null);

        Assert.assertTrue(nextCalled[0]);
    }

}
