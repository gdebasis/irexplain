/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.sampler;

import ibm.drl.irexplanation.samplegen.TermWeights;

/**
 *
 * @author dganguly
 */
public interface Sampler {
    public TermWeights nextSample() throws Exception; 
}
