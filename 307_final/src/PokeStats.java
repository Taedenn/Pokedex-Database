public enum PokeStats 
{
    HP(1), ATK(2), DEF(3), SATK(4), SDEF(5), SPD(6), TOTAL(7);
 
    private PokeStats(final int id) {
        this.id = id;
    }
 
    private int id;
 
    public int getId() {
        return id;
    }
}
