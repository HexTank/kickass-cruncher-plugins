package se.triad.kickass;

import static se.triad.kickass.Utils.toHexString;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import cml.kickass.plugins.interf.IEngine;
import cml.kickass.plugins.interf.IMemoryBlock;
import cml.kickass.plugins.interf.IModifier;
import cml.kickass.plugins.interf.IValue;

public abstract class AbstractCruncher implements IModifier{

    public static enum Options {
        FORWARD_CRUNCHING,
        USE_LITERALS,
        APPEND_IN_LOAD,
        VALIDATE_SAFETY_OFFSET,
        REVERSE_OUTPUT,
        OUTPUT_BLOCK_OFFSETS,
        MAXIMUM_OFFSET_SIZE
    }


    protected abstract String getSyntax();

    @Override
    public byte[] execute(List<IMemoryBlock> blocks, IValue[] values, IEngine engine) {

        EnumMap<Options,Object> opts = new EnumMap<AbstractCruncher.Options, Object>(Options.class);

        validateArguments(opts, blocks,values,engine);

        blocks = preTransformBlocks(blocks);
        List<CrunchedObject> crunchedObjects = new ArrayList<CrunchedObject>();

        for (IMemoryBlock block: blocks){

            CrunchedObject crunchedObj = crunch(block, opts, engine);
            crunchedObjects.add(crunchedObj);

            int percent = 100 * crunchedObj.data.length / block.getBytes().length;
            engine.printNow(getName() + ": " +block.getName() +  " " +toHexString(block.getStartAddress()) + " - " + toHexString(block.getStartAddress()-1+block.getBytes().length) +
                    " Packed size " + toHexString(crunchedObj.data.length)+" ("+percent+ "%) " + formatAddress(crunchedObj.address));
        }

        validateResult(blocks, opts, engine, crunchedObjects);

        return finalizeData(blocks, opts, engine, crunchedObjects);

    }

    protected abstract String formatAddress(int address);

    protected abstract CrunchedObject crunch(IMemoryBlock block,
            EnumMap<Options, Object> opts, IEngine iEngine);

    protected abstract byte[] finalizeData(List<IMemoryBlock> blocks, EnumMap<Options, Object> options, IEngine engine,
            List<CrunchedObject> crunchedObjects);

    protected List<IMemoryBlock> preTransformBlocks(List<IMemoryBlock> blocks) {
        return blocks;
    }

    protected void validateResult(List<IMemoryBlock> blocks, EnumMap<Options, Object> opts, IEngine engine,
            List<CrunchedObject> exoObjects) {

        if (opts.containsKey(Options.VALIDATE_SAFETY_OFFSET)){

            for (int i = 0; i < blocks.size(); i++){
                final int safetyOffset = exoObjects.get(i).address;
                final int finalSize = exoObjects.get(i).data.length;
                final int memAddr = (Integer) opts.get(Options.VALIDATE_SAFETY_OFFSET);
                final boolean forwardCrunching = opts.containsKey(Options.FORWARD_CRUNCHING);
                final int min = blocks.get(i).getStartAddress();
                final int max = blocks.get(i).getStartAddress()+blocks.get(i).getBytes().length;

                if ( (!forwardCrunching && memAddr > min-safetyOffset && memAddr < max) ||
                        (forwardCrunching && !(memAddr >= min+safetyOffset || min >= memAddr+finalSize)))
                {
                    String error = "WARNING! Crunched data '" + blocks.get(i).getName() + "' in block["+i+"] cannot be decompressed at "+ toHexString(memAddr) +
                            " Safety distance is " + toHexString(safetyOffset) + " Decompressed data span " + toHexString(min) + " - " + toHexString(max);
                    if (forwardCrunching)
                        error = error + "\nPlace your data >= " + toHexString(min+safetyOffset) + " or <= " + toHexString(min-finalSize);
                    else
                        error = error + "\nPlace your data <= "+toHexString(min-safetyOffset) + " or >= " + toHexString(max);

                    engine.error(error);
                }
            }
        }

    }

    protected abstract void validateArguments(EnumMap<Options, Object> opts, List<IMemoryBlock> blocks, IValue[] values,
            IEngine engine);

    protected static void addBooleanOption(IValue[] values, int index,
            EnumMap<Options, Object> opts, Options opt, boolean defaultValue) {
        if ( values.length > index && values[index].getBoolean() || values.length <= index && defaultValue) {
            opts.put(opt,null);
        }
    }
    protected static void addIntegerOption(IValue[] values, int index,
            EnumMap<Options, Object> opts, Options opt, int defaultValue) {
        int val = defaultValue;
        if ( values.length > index && values[index].hasIntRepresentation()) {
            val = values[index].getInt();
            if (values[index].getInt() < 1 || values[index].getInt() > 65536)
                throw new IllegalArgumentException("Maximum offset size must be a positive 16-bit integer");
        }
        opts.put(opt, new Integer(val));
    }

    protected static void addSafetyOffsetCheckOption(IValue[] values,
            int index, EnumMap<Options, Object> opts) {
        if ( values.length > index) {
            if (values[index].hasIntRepresentation() && values[index].getInt() >= 0 && values[index].getInt() < 65536) {
                opts.put(Options.VALIDATE_SAFETY_OFFSET, values[index].getInt() );
            } else if (!values[index].hasIntRepresentation()){
                throw new IllegalArgumentException("Not an integer or value of out range: "  + values[index].getInt());
            }
        }
    }
}