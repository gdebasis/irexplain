/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.regression;

/**
 *
 * @author Procheta
 */
public class EvaluationMetric {
    double []fidelityScore;
    FeatureSpace baseFeature;
    FeatureSpace feature;
    double []coefficients;
    int [] posnegCount;
    
    public EvaluationMetric(FeatureSpace feature, double[] coeffs){
        baseFeature=feature;
        coefficients = coeffs;
        fidelityScore = new double[coeffs.length];
        posnegCount = new int[2*coeffs.length];
    }
    
    public void computeFidelityScore(FeatureSpace feature){
        double score_x = ((baseFeature.tf - feature.tf)/baseFeature.tf)*coefficients[0];
        double score_y = ((baseFeature.docLength - feature.docLength)/baseFeature.docLength)*coefficients[1];
        double score_z = ((baseFeature.df - feature.df)/baseFeature.df)*coefficients[2];
        fidelityScore[0] = score_x;
        fidelityScore[1]= score_y;
        fidelityScore[2] = score_z;
        int j = 0;
        for(int i = 0; i < posnegCount.length; i=i+2){
            if(fidelityScore[j++] > 0)
                posnegCount[i]+=1;
            else
               posnegCount[i+1]+=1;         
        }
    }
}
