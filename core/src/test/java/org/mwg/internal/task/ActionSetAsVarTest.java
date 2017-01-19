package org.mwg.internal.task;

import org.junit.Assert;
import org.junit.Test;
import org.mwg.Callback;
import org.mwg.task.ActionFunction;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

import static org.mwg.internal.task.CoreActions.defineAsGlobalVar;
import static org.mwg.internal.task.CoreActions.inject;
import static org.mwg.task.Tasks.newTask;

public class ActionSetAsVarTest extends AbstractActionTest {

    @Test
    public void test() {
        initGraph();
        newTask()
                .then(inject("hello"))
                .then(defineAsGlobalVar("myVar"))
                .thenDo(new ActionFunction() {
                    @Override
                    public void eval(TaskContext ctx) {
                        Assert.assertEquals(ctx.result().get(0), "hello");
                        Assert.assertEquals(ctx.variable("myVar").get(0), "hello");
                        ctx.continueTask();
                    }
                })
                .execute(graph, new Callback<TaskResult>() {
                    @Override
                    public void on(TaskResult result) {
                        Assert.assertNotEquals(result.size(), 0);
                    }
                });
        removeGraph();
    }

}