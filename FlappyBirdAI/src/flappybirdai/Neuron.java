package flappybirdai;

import java.util.ArrayList;
import java.util.List;

//Contains a value that is stored between 0 and 1 defined by a sigmoid
public class Neuron {
    public static double sigmoid(final double x) {
        return 2.0 / (1.0 + Math.exp(-4.9 * x)) - 1.0;
    }

    public double              value  = 0.0;
    public final List<Synapse> inputs = new ArrayList<Synapse>();
    
}
