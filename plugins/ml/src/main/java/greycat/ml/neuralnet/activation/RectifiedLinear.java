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
package greycat.ml.neuralnet.activation;


class RectifiedLinear implements Activation {
    private double slope;

    public RectifiedLinear(double slope) {
        this.slope = slope;
    }

    @Override
    public double forward(double x) {
        if (x >= 0) {
            return x;
        } else {
            return x * slope;
        }
    }

    @Override
    public double backward(double x, double fct) {
        if (x >= 0) {
            return 1.0;
        } else {
            return slope;
        }
    }
}
