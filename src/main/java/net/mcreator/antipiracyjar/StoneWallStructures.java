package net.mcreator.antipiracyjar;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class StoneWallStructures {
    static int localX(StoneWorldLayout.Room r, int wx, int wz) {
        int dx=wx-r.x,dz=wz-r.z;
        return switch(r.rotation){case 1 -> dz;case 2 -> -dx;case 3 -> -dz;default -> dx;};
    }
    static int localZ(StoneWorldLayout.Room r, int wx, int wz) {
        int dx=wx-r.x,dz=wz-r.z;
        return switch(r.rotation){case 1 -> -dx;case 2 -> -dz;case 3 -> dx;default -> dz;};
    }
    static int front(StoneWorldLayout.Room r) {return (r.rotation%2==0?r.depth:r.width)-3;}
    static boolean contains(StoneWorldLayout.Room r,int wx,int wz) {
        if(r.kind<40)return false;
        int x=localX(r,wx,wz),z=localZ(r,wx,wz)-front(r);
        return Math.abs(x)<=4 && z>=0 && z<=7;
    }
    static BlockState at(StoneWorldLayout.Room r,int wx,int wy,int wz) {
        int x=localX(r,wx,wz),z=localZ(r,wx,wz)-front(r),y=wy-r.floor;
        if(y<0||y>6)return null;
        BlockState state=block(r.kind-40,x,y,z);
        return state.rotate(switch(r.rotation){case 1 -> Rotation.CLOCKWISE_90;case 2 -> Rotation.CLOCKWISE_180;case 3 -> Rotation.COUNTERCLOCKWISE_90;default -> Rotation.NONE;});
    }
    private static BlockState block(int kind,int x,int y,int z) {
        if(y==0)return (kind==1||kind==6||kind==7?Blocks.COBBLESTONE:Blocks.OAK_PLANKS).defaultBlockState();
        if(y==6)return (kind==5?Blocks.GLASS:Blocks.COBBLESTONE).defaultBlockState();
        if(Math.abs(x)==4||z==0||z==7){
            if(z==0 && x==0 && y<=2)return Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH)
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,y==1?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);
            if(z==0&&Math.abs(x)==2&&(y==2||y==3))return Blocks.GLASS.defaultBlockState();
            if(kind==6&&z==0&&Math.abs(x)<=2&&y<=4)return Blocks.AIR.defaultBlockState();
            if(kind==5&&y>=2&&Math.abs(x)!=4)return Blocks.GLASS.defaultBlockState();
            if(Math.abs(x)==4&&(z==0||z==7))return Blocks.OAK_LOG.defaultBlockState();
            return (kind==1||kind==6||kind==7||kind==9?Blocks.COBBLESTONE:Blocks.OAK_PLANKS).defaultBlockState();
        }
        if(y==4&&x==0&&z==6)return Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH);
        if((kind==0||kind==4)&&x==-2&&(z==4||z==5)&&y==1)return StoneWorldGenerator.Column.bed(z==5)
                .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
        if(kind==0&&x==2&&z==5&&y==1)return Blocks.CRAFTING_TABLE.defaultBlockState();
        if(kind==1&&x==2&&z>=3&&z<=5&&y==1)return Blocks.FURNACE.defaultBlockState();
        if(kind==2&&Math.abs(x)==2&&z>=3&&y==1)return (x<0?Blocks.CRAFTING_TABLE:Blocks.FURNACE).defaultBlockState();
        if(kind==3&&x>=2&&z>=3&&y<=2)return Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z);
        if(kind==4&&x==2&&(z==4||z==5)&&y==1)return StoneWorldGenerator.Column.bed(z==5).setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
        if(kind==5&&Math.abs(x)==2&&z>=2&&z<=5){if(y==1)return Blocks.DIRT.defaultBlockState();if(y==2)return Blocks.OAK_SAPLING.defaultBlockState();}
        if(kind==6&&x==0&&y==1)return Blocks.RAIL.defaultBlockState();
        if(kind==6&&Math.abs(x)==3&&z%3==0&&y<=4)return Blocks.OAK_FENCE.defaultBlockState();
        if(kind==7&&Math.abs(x)<=1&&z>=3&&z<=5&&y==1)return Blocks.COBBLESTONE.defaultBlockState();
        if(kind==7&&x==0&&z==4&&y==2)return Blocks.WATER.defaultBlockState();
        if(kind==7&&y==2&&Math.abs(x)<=1&&z>=3&&z<=5)return Blocks.COBBLESTONE.defaultBlockState();
        if(kind==8&&Math.abs(x)==3&&z>=2&&y<=3)return Blocks.BOOKSHELF.defaultBlockState();
        if(kind==9&&Math.abs(x)==2&&z%2==0&&y==1)return Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
        if(kind==9&&x==0&&z==5&&y==1)return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }
}
