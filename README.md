EasySRL builds logical forms for natural language sentences, by jointly modelling CCG and semantic role labelling. It uses an efficient A* parsing algorithm, meaning it can be used to process large corpora. A demo is online here: http://lil.cs.washington.edu/easysrl/demo.cgi

If you use the parser for research, please cite the following paper:

@inproceedings{lewis:2015,
  title={Joint A* CCG Parsing and Semantic Role Labelling},
  author={Lewis, Mike and He, Luheng and Zettlemoyer, Luke},
  booktitle={Empirical Methods in Natural Language Processing},
  year={2015}
}

A pretrained model is available here: https://drive.google.com/file/d/0B7AY6PGZ8lc-R1E3aTA5WG54bWM/view?usp=sharing

If parsing questions, use this model instead: https://drive.google.com/file/d/0B7AY6PGZ8lc-bVZXSTM0RWlrVTg/view?usp=sharing

Basic usage:
    java -jar easysrl.jar --model modelFolder

For CCG syntactic output, use:
    java -jar easysrl.jar --model modelFolder --outputFormat ccgbank

For semantic role labelling output, use:
    java -jar easysrl.jar --model modelFolder --outputFormat srl

To get n-best parses:
    java -jar easysrl.jar --model modelFolder --nbest 10 --supertaggerbeam 0.001


Please contact Mike Lewis with any questions or feature requests (email address in the paper).
