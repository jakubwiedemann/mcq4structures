package pl.poznan.put.comparison;

import pl.poznan.put.gui.window.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.poznan.put.circular.Angle;
import pl.poznan.put.circular.samples.AngleSample;
import pl.poznan.put.comparison.exception.IncomparableStructuresException;
import pl.poznan.put.comparison.global.GlobalComparator;
import pl.poznan.put.comparison.global.GlobalMatrix;
import pl.poznan.put.comparison.global.GlobalResult;
import pl.poznan.put.comparison.global.LCSGlobalResult;
import pl.poznan.put.comparison.global.ParallelGlobalComparator;
import pl.poznan.put.matching.FragmentMatch;
import pl.poznan.put.matching.MCQMatcher;
import pl.poznan.put.matching.ResidueComparison;
import pl.poznan.put.matching.SelectionFactory;
import pl.poznan.put.matching.SelectionMatch;
import pl.poznan.put.matching.StructureSelection;
import pl.poznan.put.pdb.PdbParsingException;
import pl.poznan.put.pdb.analysis.MoleculeType;
import pl.poznan.put.pdb.analysis.PdbCompactFragment;
import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.protein.torsion.ProteinTorsionAngleType;
import pl.poznan.put.rna.torsion.RNATorsionAngleType;
import pl.poznan.put.structure.tertiary.StructureManager;
import pl.poznan.put.torsion.MasterTorsionAngleType;
import pl.poznan.put.torsion.TorsionAngleDelta;
import pl.poznan.put.torsion.TorsionAngleDelta.State;
import pl.poznan.put.utility.TabularExporter;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.*;

/**
 * Implementation of LCS global similarity measure based on torsion angle
 * representation.
 *
 * @author Tomasz Zok (tzok[at]cs.put.poznan.pl)
 * @co-author Jakub Wiedemann (jakub.wiedemann[at]cs.put.poznan.pl)
 */
public class LCS implements GlobalComparator {
    private final static Logger LOGGER = LoggerFactory.getLogger(LCS.class);

    private final List<MasterTorsionAngleType> angleTypes;
    public double mcqValue;

    public LCS() {
        super();
        angleTypes = new ArrayList<>();
        angleTypes.addAll(Arrays.asList(RNATorsionAngleType.mainAngles()));
        angleTypes.addAll(Arrays.asList(ProteinTorsionAngleType.mainAngles()));

        JFrame frame = new JFrame("MCQ value");
        mcqValue = Math.toRadians(Double.parseDouble(JOptionPane.showInputDialog(frame, "Set threshold in degrees [\u00b0]")));

    }
 
    public LCS(MoleculeType moleculeType) {
        super();

        switch (moleculeType) {
            case PROTEIN:
                angleTypes =
                        Arrays.asList(ProteinTorsionAngleType.mainAngles());
                break;
            case RNA:
                angleTypes = Arrays.asList(RNATorsionAngleType.mainAngles());
                break;
            case UNKNOWN:
            default:
                angleTypes = Collections.emptyList();
                break;
        }
    }

    public LCS(List<MasterTorsionAngleType> angleTypes) {
        super();
        this.angleTypes = angleTypes;
    }


    public static void main(String[] args)
            throws IOException, PdbParsingException, InterruptedException {
        if (args.length < 2) {
            System.err.println("You must specify at least 2 structures");
            return;
        }

        List<StructureSelection> selections = new ArrayList<>();

        for (String arg : args) {
            File file = new File(arg);

            if (!file.canRead()) {
                System.err.println("Failed to open file: " + file);
                return;
            }

            PdbModel structure = StructureManager.loadStructure(file).get(0);
            selections.add(SelectionFactory.create(file.getName(), structure));
        }

        ParallelGlobalComparator comparator =
                new ParallelGlobalComparator(new LCS(), selections,
                                             new ParallelGlobalComparator
                                                     .ProgressListener() {
                                                 @Override
                                                 public void setProgress(
                                                         int progress) {
                                                     // do nothing
                                                 }

                                                 @Override
                                                 public void complete(
                                                         GlobalMatrix matrix) {
                                                     try {
                                                         TabularExporter
                                                                 .export(matrix.asExportableTableModel(),
                                                                         System.out);
                                                     } catch (IOException e) {
                                                         LCS.LOGGER
                                                                 .error("Failed to output distance matrix",
                                                                        e);
                                                     }
                                                 }
                                             });

        comparator.start();
        comparator.join();
    }

    @Override
    public GlobalResult compareGlobally(StructureSelection target,
                                        StructureSelection model)
            throws IncomparableStructuresException {
			//	if (model.getName().equals("1Q9A")){
        MCQMatcher matcher = new MCQMatcher(angleTypes);
        SelectionMatch matches = matcher.matchSelections(target, model);

        if (matches == null || matches.getFragmentCount() == 0) {
            throw new IncomparableStructuresException(
                    "No matching fragments found");
        }

        List<Angle> deltas = new ArrayList<>();

        for (FragmentMatch fragmentMatch : matches.getFragmentMatches()) {
            for (ResidueComparison residueComparison : fragmentMatch
                    .getResidueComparisons()) {
                for (MasterTorsionAngleType angleType : angleTypes) {
                    TorsionAngleDelta angleDelta =
                            residueComparison.getAngleDelta(angleType);
                            
                    if (angleDelta.getState() == State.BOTH_VALID) {
                        deltas.add(angleDelta.getDelta());
                    }
                }
            }
        }

        AngleSample angleSample = new AngleSample(deltas);
        /*System.out.print(angleSample.getMeanDirection().getRadians());*/
        /*System.out.print(model.getName()+"\n");*/
        /*System.out.print(model +"\n");*/
        /*System.out.print(model.getResidues().subList(1,model.getResidues().size()));*/
        double maxMcqVal = 0.0;
        int longest = 0;
        boolean found = false;
		boolean exist = false;
		int count = 0;
        
        RefinementResult maxRefinementResult = new RefinementResult(matches, new AngleSample(deltas), model, target);
		/*System.out.print("" +angleSample.getMeanDirection().getRadians());*/
        if (angleSample.getMeanDirection().getRadians()<mcqValue) {
			/*System.out.print("powinno zwrocic");*/
            return new LCSGlobalResult(getName(), matches, new AngleSample(deltas), model, target, model, 1);
        }
        else{
            int s;
            int  l = 0;
            int p = target.getResidues().size()-1;
            while (l <= p){
                s = (l + p) / 2;
                found = false;
				
                for(int j=0; j+s<=target.getResidues().size(); j++){
					try{
                    List<PdbResidue> fragmentResidues = target.getResidues().subList(j,j+s);
                    StructureSelection target1 = new StructureSelection(target.getName(), fragmentResidues);
                    RefinementResult localRefinementResult = refinement(model, target1);
                    if (localRefinementResult.getSample().getMeanDirection().getRadians()<=mcqValue && longest<=localRefinementResult.getMatch().getResidueLabels().size()){
                        longest=localRefinementResult.getMatch().getResidueLabels().size();
                        maxRefinementResult = localRefinementResult;
						if (found == false)
						{
						count = 0;
						}
                        found = true;
						exist = true;
						count += 1;
						/*if (count ==4){
						break;
						}*/
                    }
					}
					catch(Exception e) {
               //System.out.print("Nothing\n");
               // I want to go to next interation

           }
                }
                if (found == true){
                    l = s + 1;
                }
                else{
                    p = s - 1; 
                }

            } 
            
            if (exist == true){
            return new LCSGlobalResult(getName(), maxRefinementResult.getMatch(), maxRefinementResult.getSample(), maxRefinementResult.getModel(), maxRefinementResult.getTarget(), model, count);
			}
			else {
			return null;	
			}
        }
        //}
       // else{
        // return null;
       // }
    }
    public class RefinementResult{
        SelectionMatch selectionMatch;
        AngleSample angleSample;
        StructureSelection model;
        StructureSelection target;


        public RefinementResult(SelectionMatch match, AngleSample sample, StructureSelection s1, StructureSelection s2){
            selectionMatch = match;
            angleSample = sample;
            model = s1;
            target = s2;

         }
         public SelectionMatch getMatch(){
            return selectionMatch;

        }
        public AngleSample getSample(){
             return angleSample;
        }
        public StructureSelection getModel(){
            return model;
        }
        public StructureSelection getTarget(){
            return target;
        }

        
    }


    
    public RefinementResult refinement(StructureSelection target,
                                        StructureSelection model
                                        )
            throws IncomparableStructuresException {
        MCQMatcher matcher = new MCQMatcher(angleTypes);
        SelectionMatch matches = matcher.matchSelections(target, model);

        if (matches == null || matches.getFragmentCount() == 0) {
            throw new IncomparableStructuresException(
                    "No matching fragments found");
        }

        List<Angle> deltas = new ArrayList<>();

        for (FragmentMatch fragmentMatch : matches.getFragmentMatches()) {
            for (ResidueComparison residueComparison : fragmentMatch
                    .getResidueComparisons()) {
                for (MasterTorsionAngleType angleType : angleTypes) {
                    TorsionAngleDelta angleDelta =
                            residueComparison.getAngleDelta(angleType);

                    if (angleDelta.getState() == State.BOTH_VALID) {
                        deltas.add(angleDelta.getDelta());
                    }
                }
            }
        }
        /*AngleSample angleSample = new AngleSample(deltas);*/
        RefinementResult tempRefinementResult = new RefinementResult(matches, new AngleSample(deltas), model, target);
        return tempRefinementResult;
    }


    @Override
    public String getName() {
        return "LCS (Sequence independent)";
    }

    @Override
    public boolean isAngularMeasure() {
        return true;
    }

}
