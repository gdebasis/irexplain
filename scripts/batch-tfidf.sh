#!/bin/bash


for ratio in 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9
do

PROPFILE=pointwise.tfidf.$ratio.properties 
cat > $PROPFILE << EOF1

index=data/trec/index
stopfile=stop.txt
field.content=words
field.id=id

#fraction of original doc length to be used as an explanation sample
sample.size.ratio=$ratio
term.importance.sampler.lambda=0.6
sampler.seed=123456

query.file=data/trec/topics/topics.401-450.xml
query.start=401
query.end=450

#explanation scores are analyzed with respect to the top 1000 docs
#which gives a local view of the index
analysis.numtop=1000
explanation.numtop=5

#current models supported -
#lmjm
#lmdir
#bm25
#todo: drmm
explanation.model=lmjm

explanation.type=pointwise

#sampler types
#allowable types -- tfidf/mask
#importance_sampling_tfidf
sampler.type=tfidf
sampler.numsamples=200

sampling.outfile=samples/pointwise/samples_${ratio}_tfidf.txt

#size of a chunk
maskingsampler.chunksize=10
#Probability that this chunk is visible
maskingsampler.visprob=0.2

EOF1

echo "Generating tf-idf samples with ratio $ratio"
 
mvn exec:java@point -Dexec.args="$PROPFILE"

rm $PROPFILE 

done

