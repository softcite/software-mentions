package org.grobid.trainer;

import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.main.LibraryLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.Arrays;
import java.io.File;

/**
 * Training application for training a target model.
 *
 * @author Patrice Lopez
 */
public class SoftwareTrainerRunner {

    private static final String USAGE = "Usage: {0 - train, 1 - evaluate, 2 - split, train and evaluate, 3 - eval with n-fold} "
            + "{software, software_type, software_disambiguation, software_doc_level} "
            + "-s { [0.0 - 1.0] - split ratio, optional} "
            + "-b {epsilon, window, nbMax}"
            + "-t NBThreads";

    enum RunType {
        TRAIN, EVAL, SPLIT, EVAL_N_FOLD;

        public static RunType getRunType(int i) {
            for (RunType t : values()) {
                if (t.ordinal() == i) {
                    return t;
                }
            }

            throw new IllegalStateException("Unsupported RunType with ordinal " + i);
        }
    }

    /**
     * Initialize the batch.
     */
    protected static void initProcess(String grobidHome) {
        try {
            if (grobidHome == null)
                grobidHome = "../grobid-home/";
            
            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(grobidHome));
            grobidHomeFinder.findGrobidHomeOrFail();
            GrobidProperties.getInstance(grobidHomeFinder);
        } catch (final Exception exp) {
            System.err.println("Grobid initialisation failed: " + exp);
        }
    }

    protected static void initProcess() {
        GrobidProperties.getInstance();
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            throw new IllegalStateException(USAGE);
        }

        RunType mode = RunType.getRunType(Integer.parseInt(args[0]));
        if ((mode == RunType.SPLIT) && (args.length < 5)) {
            throw new IllegalStateException(USAGE);
        }

        String path2GbdHome = null;
        SoftwareConfiguration conf = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            File yamlFile = new File("resources/config/config.yml");
            yamlFile = new File(yamlFile.getAbsolutePath());
            conf = mapper.readValue(yamlFile, SoftwareConfiguration.class);

            //String path2GbdHome = SoftwareProperties.get("grobid.home");
            path2GbdHome = conf.getGrobidHome();
        } catch(Exception e) {
            System.err.println("Invalid config file: resources/config/config.yml");
        }
        String grobidHome = args[2];
        if (grobidHome != null) {
            path2GbdHome = grobidHome;
        }
        System.out.println("path2GbdHome=" + path2GbdHome);
        initProcess(path2GbdHome);

        if (conf != null && (conf.getModel("software") != null || conf.getModel("software-type") != null))
            for (ModelParameters model : conf.getModels()) 
                GrobidProperties.getInstance().addModel(model);
        LibraryLoader.load();

        Double split = 0.0;
        double epsilon = 0.000001;
        int window = 20;
        int nbMaxIterations = 0;
        int numFolds = 10;

        for (int i = 0; i < args.length; i++) {
            if (i == 4) {
                int nbTreadsInt = 0;
                try {
                    nbTreadsInt = Integer.parseInt(args[i]);
                } catch (Exception e) {
                    System.err.println("Warning: the thread number parameter is not a valid integer, " + args[i] + " - using 0 as default thread number");
                    e.printStackTrace();
                }
                GrobidProperties.getInstance().setWapitiNbThreads(nbTreadsInt);
            } else if (i == 3) {
                String splitRatio = args[i];
                try {
                    split = Double.parseDouble(args[i]);
                } catch (Exception e) {
                    throw new IllegalStateException("Invalid split value: " + args[i]);
                }
            } else if (args[i].equals("-n")) {
                if (i + 1 == args.length) {
                    throw new IllegalStateException("Missing number of folds value. ");
                }
                try {
                    numFolds = Integer.parseInt(args[i + 1]);
                } catch (Exception e) {
                    throw new IllegalStateException("Invalid number of folds value: " + args[i + 1]);
                }
            }
        }

        if (path2GbdHome == null) {
            throw new IllegalStateException(USAGE);
        }

        if ("software_type".equals(args[1])) { 
            SoftwareTypeTrainer trainer = new SoftwareTypeTrainer();

            trainer.setSoftwareConf(conf);

            switch (mode) {
                case TRAIN:
                    AbstractTrainer.runTraining(trainer);
                    break;
                case EVAL:
                    System.out.println(AbstractTrainer.runEvaluation(trainer));
                    break;
                case SPLIT:
                    System.out.println(AbstractTrainer.runSplitTrainingEvaluation(trainer, split));
                    break;
                case EVAL_N_FOLD:
                    if(numFolds == 0) {
                        throw new IllegalArgumentException("N should be > 0");
                    }
                    System.out.println(AbstractTrainer.runNFoldEvaluation(trainer, numFolds));
                    break;
                default:
                    throw new IllegalStateException("Invalid RunType: " + mode.name());
            }
            System.exit(0);
        } 

        SoftwareTrainer trainer = null;
        if ("software".equals(args[1])) { 
            trainer = new SoftwareTrainer();
        } else if ("software_disambiguation".equals(args[1])) {  
            trainer = new SoftwareExtendedEval();
            ((SoftwareExtendedEval)trainer).setDisambiguate(true);
        } else if ("software_doc_level".equals(args[1])) { 
            trainer = new SoftwareExtendedEval();
            ((SoftwareExtendedEval)trainer).setDocLevel(true);
        } else if ("software_disamb_doc_level".equals(args[1])) { 
            trainer = new SoftwareExtendedEval();
            ((SoftwareExtendedEval)trainer).setDisambiguate(true);
            ((SoftwareExtendedEval)trainer).setDocLevel(true);
        } else {
            System.out.println("type of train/eval scope not expected: " + args[1]);
            System.exit(0);
        }
        trainer.setSoftwareConf(conf);

        switch (mode) {
            case TRAIN:
                AbstractTrainer.runTraining(trainer);
                break;
            case EVAL:
                System.out.println(AbstractTrainer.runEvaluation(trainer));
                break;
            case SPLIT:
                System.out.println(AbstractTrainer.runSplitTrainingEvaluation(trainer, split));
                break;
            case EVAL_N_FOLD:
                if(numFolds == 0) {
                    throw new IllegalArgumentException("N should be > 0");
                }
                System.out.println(AbstractTrainer.runNFoldEvaluation(trainer, numFolds));
                break;
            default:
                throw new IllegalStateException("Invalid RunType: " + mode.name());
        }
        System.exit(0);
    }
}
