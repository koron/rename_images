#!/usr/local/bin/perl
#
# show_exif_times.pl - Show date information in EXIF of a JPEG file.

use utf8;
use strict;
use warnings;

use File::Next;
use Image::ExifTool;

my $EXIFTOOL = Image::ExifTool->new();

my $files = File::Next::files($ARGV[0]);
while (defined(my $file = $files->())) {
    &check_times($file);
}

sub check_times
{
    my ($filename) = @_;
    print "$filename:\n";
    my $info = $EXIFTOOL->ImageInfo($filename);
    foreach my $key (sort keys %$info) {
        if ($key =~ m/date/i) {
            print "    $key => $info->{$key}\n";
        }
    }
}
