EasySRL is a joint CCG and semantic role labelling parser.

If you use the parser for research, please cite the following paper:

@inproceedings{lewis:2015,
  title={Joint A* CCG Parsing and Semantic Role Labelling},
  author={Lewis, Mike and He, Luheng and Zettlemoyer, Luke},
  booktitle={Empirical Methods in Natural Language Processing},
  year={2015}
}

A pre-trained model is available here: https://drive.google.com/folderview?id=0B7AY6PGZ8lc-cWhEQ3FYXzljWGc&usp=sharing

Basic usage:
    java -jar easysrl.jar --model modelFolder

For CCG (rather than SRL) output, use:
    java -jar easysrl.jar --model modelFolder --outputFormat ccgbank

Please contact Mike Lewis with any questions or feature requests (email address in the paper).
